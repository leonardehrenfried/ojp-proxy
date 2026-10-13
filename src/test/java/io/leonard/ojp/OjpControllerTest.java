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

        server = ApplicationContext.run(EmbeddedServer.class,
                Map.of("micronaut.server.port", -1, "micronaut.http.services.pelias.url", "http://localhost:" + pelias.getAddress().getPort()));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        pelias.stop(0);
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
    void rejectsNonLocationInformationRequests() {
        var e = org.junit.jupiter.api.Assertions.assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(
                        HttpRequest.POST("/ojp", "<foo/>").contentType(MediaType.APPLICATION_XML_TYPE), String.class));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }
}
