package io.leonard.ojp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import de.vdv.ojp20.OJPLocationInformationDeliveryStructure;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OjpControllerTest {

    static HttpServer pelias;
    static final AtomicReference<String> peliasQuery = new AtomicReference<>();
    static HttpServer otp;
    static final AtomicReference<String> otpRequest = new AtomicReference<>();
    static final byte[] OTP_RESPONSE = "<OJP xmlns=\"http://www.vdv.de/ojp\" version=\"2.0\"/>".getBytes(StandardCharsets.UTF_8);
    static EmbeddedServer server;
    static HttpClient client;

    @BeforeAll
    static void start() throws IOException {
        var body = OjpControllerTest.class.getResourceAsStream("/pelias-autocomplete.json").readAllBytes();
        pelias = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pelias.createContext("/v1/autocomplete", exchange -> {
            peliasQuery.set(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        pelias.start();

        otp = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        otp.createContext("/otp/ojp/v2", exchange -> {
            otpRequest.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, OTP_RESPONSE.length);
            exchange.getResponseBody().write(OTP_RESPONSE);
            exchange.close();
        });
        otp.start();

        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", -1,
                "micronaut.http.services.pelias.url", "http://localhost:" + pelias.getAddress().getPort(),
                "micronaut.http.services.otp.url", "http://localhost:" + otp.getAddress().getPort() + "/otp/ojp/v2/"));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        pelias.stop(0);
        otp.stop(0);
    }

    @Test
    void answersLocationInformationRequestFromPelias() throws Exception {
        var xml = OjpControllerTest.class.getResourceAsStream("/lir-request.xml").readAllBytes();
        var response = client.toBlocking().retrieve(
                HttpRequest.POST("/ojp", xml).contentType(MediaType.APPLICATION_XML_TYPE), String.class);

        var query = java.net.URLDecoder.decode(peliasQuery.get(), StandardCharsets.UTF_8);
        assertTrue(query.contains("text=bolzano"), query);
        assertTrue(query.contains("boundary.rect.min_lat=10.1"), query);
        assertTrue(query.contains("layers=stops,address"), query);

        var parser = new OjpParser();
        var ojp = parser.parse(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
        var delivery = (OJPLocationInformationDeliveryStructure) ojp.getOJPResponse().getServiceDelivery()
                .getAbstractFunctionalServiceDelivery().getFirst().getValue();
        assertEquals("42", delivery.getRequestMessageRef().getValue());
        assertEquals(4, delivery.getRest().size());
    }

    @Test
    void forwardsOtherRequestsToOtp() throws Exception {
        var xml = new String(OjpControllerTest.class
                .getResourceAsStream("/xsd/2.0/examples/switzerland/Trip_001_Request.xml").readAllBytes(), StandardCharsets.UTF_8);
        var response = client.toBlocking().exchange(
                HttpRequest.POST("/ojp", xml).contentType(MediaType.APPLICATION_XML_TYPE), String.class);

        assertEquals("POST /otp/ojp/v2/ " + xml, otpRequest.get());
        assertEquals(new String(OTP_RESPONSE, StandardCharsets.UTF_8), response.body());
        assertEquals(MediaType.APPLICATION_XML_TYPE, response.getContentType().orElseThrow());
    }

    @Test
    void rejectsInvalidRequests() {
        var e = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(
                        HttpRequest.POST("/ojp", "<foo/>").contentType(MediaType.APPLICATION_XML_TYPE), String.class));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }
}
