package io.leonard.ojp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.vdv.ojp20.OJPLocationInformationRequestStructure;
import de.vdv.ojp20.PlaceResultStructure;
import io.micronaut.serde.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocationInformationMapperTest {

    final LocationInformationMapper mapper = new LocationInformationMapper();

    OJPLocationInformationRequestStructure request() throws Exception {
        var parser = new OjpParser();
        try (var xml = getClass().getResourceAsStream("/lir-request.xml")) {
            return parser.locationInformationRequests(parser.parse(xml)).getFirst();
        }
    }

    @Test
    void mapsRequestToPeliasParams() throws Exception {
        assertEquals(
                Map.of(
                        "text", "bolzano",
                        "boundary.rect.min_lat", "10.1",
                        "boundary.rect.min_lon", "-8.92482",
                        "boundary.rect.max_lat", "57.6628",
                        "boundary.rect.max_lon", "40.1",
                        "layers", "stops,address",
                        "lang", "de",
                        "size", "5"),
                mapper.toPeliasParams(request()));
    }

    @Test
    void rejectsRequestWithoutName() {
        assertThrows(UnsupportedRequestException.class,
                () -> mapper.toPeliasParams(new OJPLocationInformationRequestStructure()));
    }

    @Test
    void mapsPeliasFeaturesToPlaces() throws Exception {
        PeliasResponse response;
        try (var json = getClass().getResourceAsStream("/pelias-autocomplete.json")) {
            response = ObjectMapper.getDefault().readValue(json, PeliasResponse.class);
        }
        var delivery = mapper.toDelivery(request(), response);

        assertEquals("42", delivery.getRequestMessageRef().getValue());
        assertEquals("de", delivery.getDefaultLanguage());
        List<PlaceResultStructure> results = delivery.getRest().stream()
                .map(e -> (PlaceResultStructure) e.getValue())
                .toList();
        assertEquals(4, results.size());

        var stop = results.get(0).getPlace();
        assertEquals("netex:it:apb:StopPlace:it-22021-468:", stop.getStopPlace().getStopPlaceRef().getValue());
        assertEquals("Bolzano, Stazione", stop.getStopPlace().getStopPlaceName().getText().getFirst().getValue());
        assertEquals("Bolzano, Stazione, Bozen, BZ, Italien", stop.getName().getText().getFirst().getValue());
        assertEquals(46.496637, stop.getGeoPosition().getLatitude());
        assertEquals(11.358386, stop.getGeoPosition().getLongitude());

        assertEquals("whosonfirst:locality:101751763", results.get(1).getPlace().getTopographicPlace().getTopographicPlaceCode());
        var address = results.get(2).getPlace().getAddress();
        assertEquals("Via Roma", address.getStreet());
        assertEquals("10", address.getHouseNumber());
        assertNotNull(results.get(3).getPlace().getPointOfInterest());
    }
}
