package io.leonard.ojp;

import de.vdv.ojp20.OJP;
import de.vdv.ojp20.OJPLocationInformationRequestStructure;
import de.vdv.ojp20.OJPRequestStructure;
import jakarta.inject.Singleton;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.JAXBIntrospector;
import jakarta.xml.bind.Marshaller;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/** Deserializes and serializes OJP 2.0 XML documents using the JAXB model from ojp-java-model. */
@Singleton
public class OjpParser {

    // JAXBContext is expensive to create but thread-safe, so build it once.
    private final JAXBContext context;

    public OjpParser() throws JAXBException {
        this.context = JAXBContext.newInstance(OJP.class);
    }

    public OJP parse(InputStream xml) throws JAXBException {
        // Unmarshaller is not thread-safe, so create one per call.
        var root = JAXBIntrospector.getValue(context.createUnmarshaller().unmarshal(xml));
        if (root instanceof OJP ojp) {
            return ojp;
        }
        throw new JAXBException("Root element must be <OJP>");
    }

    /** Marshals directly to {@code out} as UTF-8 without building the document in memory first. */
    public void write(OJP ojp, OutputStream out) throws JAXBException {
        var marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(ojp, out);
    }

    /** All LocationInformationRequests contained in the document's ServiceRequest, if any. */
    public List<OJPLocationInformationRequestStructure> locationInformationRequests(OJP ojp) {
        return Optional.ofNullable(ojp.getOJPRequest())
                .map(OJPRequestStructure::getServiceRequest)
                .map(sr -> sr.getAbstractFunctionalServiceRequest().stream()
                        .map(JAXBElement::getValue)
                        .filter(OJPLocationInformationRequestStructure.class::isInstance)
                        .map(OJPLocationInformationRequestStructure.class::cast)
                        .toList())
                .orElse(List.of());
    }
}
