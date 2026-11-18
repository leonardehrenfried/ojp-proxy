package io.leonard.ojp;

import de.vdv.ojp20.OJP;
import de.vdv.ojp20.OJPResponseStructure;
import de.vdv.ojp20.ObjectFactory;
import de.vdv.ojp20.siri.ParticipantRefStructure;
import de.vdv.ojp20.siri.ServiceDelivery;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import org.opentripplanner.ojp.time.XmlDateTime;

/**
 * Answers OJP LocationInformationRequests by querying Pelias and forwards every other request to OTP.
 */
@Controller("/ojp")
public class OjpController {

    private final OjpParser parser;
    private final LocationInformationMapper mapper;
    private final PeliasClient pelias;
    private final XmlStreamer streamer;
    private final OtpClient otp;
    private final ObjectFactory factory = new ObjectFactory();

    public OjpController(OjpParser parser, LocationInformationMapper mapper, PeliasClient pelias, XmlStreamer streamer,
            OtpClient otp) {
        this.parser = parser;
        this.mapper = mapper;
        this.pelias = pelias;
        this.streamer = streamer;
        this.otp = otp;
    }

    @Post(consumes = {MediaType.APPLICATION_XML, MediaType.TEXT_XML}, produces = MediaType.APPLICATION_XML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> handle(@Body byte[] body, @Header(HttpHeaders.CONTENT_TYPE) MediaType contentType)
            throws IOException {
        OJP ojp;
        try {
            ojp = parser.parse(new ByteArrayInputStream(body));
        } catch (JAXBException e) {
            var cause = e.getLinkedException() != null ? e.getLinkedException() : e;
            // JAXB appends the list of every known element, which is not useful to clients
            var message = cause.getMessage().split(" Expected elements are")[0];
            throw new UnsupportedRequestException("Invalid OJP XML: " + message);
        }
        var requests = parser.onlyLocationInformationRequests(ojp);
        if (requests.isEmpty()) {
            return otp.forward(body, contentType);
        }
        var serviceDelivery = new ServiceDelivery()
                .withResponseTimestamp(XmlDateTime.truncatedToMillis(ZonedDateTime.now()))
                .withProducerRef(new ParticipantRefStructure().withValue("ojp-proxy"))
                .withStatus(true);
        for (var request : requests) {
            var results = pelias.autocomplete(mapper.toPeliasParams(request));
            serviceDelivery.getAbstractFunctionalServiceDelivery()
                    .add(factory.createOJPLocationInformationDelivery(mapper.toDelivery(request, results)));
        }
        var response = new OJP().withVersion("2.0").withOJPResponse(new OJPResponseStructure().withServiceDelivery(serviceDelivery));
        return HttpResponse.ok(streamer.stream(out -> parser.write(response, out))).contentType(MediaType.APPLICATION_XML_TYPE);
    }

    @Error(exception = UnsupportedRequestException.class)
    public HttpResponse<String> badRequest(UnsupportedRequestException e) {
        return HttpResponse.<String>badRequest(e.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

}
