package io.leonard.ojp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OjpParserTest {

    @Test
    void parsesLocationInformationRequest() throws Exception {
        var parser = new OjpParser();
        // example shipped inside the ojp-java-model jar
        try (var xml = getClass().getResourceAsStream(
                "/xsd/2.0/examples/switzerland/LocationInformation_002_Stop_InitialInput_Request.xml")) {
            var lirs = parser.locationInformationRequests(parser.parse(xml));

            assertEquals(1, lirs.size());
            var lir = lirs.getFirst();
            assertEquals("Bern", lir.getInitialInput().getName());
            assertEquals("4712", lir.getMessageIdentifier().getValue());
        }
    }

    @Test
    void rejectsNonOjpRootElement() throws Exception {
        var parser = new OjpParser();
        var xml = """
                <OJPLocationInformationRequest xmlns="http://www.vdv.de/ojp"/>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(jakarta.xml.bind.JAXBException.class,
                () -> parser.parse(new java.io.ByteArrayInputStream(xml)));
    }

    @Test
    void returnsEmptyListForResponses() throws Exception {
        var parser = new OjpParser();
        try (var xml = getClass().getResourceAsStream(
                "/xsd/2.0/examples/switzerland/LocationInformation_002_Stop_InitialInput_Response.xml")) {
            assertTrue(parser.locationInformationRequests(parser.parse(xml)).isEmpty());
        }
    }
}
