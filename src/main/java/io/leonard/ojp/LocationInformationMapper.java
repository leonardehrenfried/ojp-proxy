package io.leonard.ojp;

import de.vdv.ojp20.AddressStructure;
import de.vdv.ojp20.GeoRestrictionsStructure;
import de.vdv.ojp20.InternationalTextStructure;
import de.vdv.ojp20.OJPLocationInformationDeliveryStructure;
import de.vdv.ojp20.OJPLocationInformationRequestStructure;
import de.vdv.ojp20.ObjectFactory;
import de.vdv.ojp20.PlaceParamStructure;
import de.vdv.ojp20.PlaceResultStructure;
import de.vdv.ojp20.PlaceStructure;
import de.vdv.ojp20.PlaceTypeEnumeration;
import de.vdv.ojp20.PointOfInterestStructure;
import de.vdv.ojp20.StopPlaceRefStructure;
import de.vdv.ojp20.StopPlaceStructure;
import de.vdv.ojp20.TopographicPlaceStructure;
import de.vdv.ojp20.siri.LocationStructure;
import de.vdv.ojp20.siri.MessageRefStructure;
import de.vdv.ojp20.siri.NaturalLanguageStringStructure;
import jakarta.inject.Singleton;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opentripplanner.ojp.time.XmlDateTime;

/** Maps OJP LocationInformationRequests to Pelias autocomplete queries and Pelias results back to OJP. */
@Singleton
public class LocationInformationMapper {

    private final ObjectFactory factory = new ObjectFactory();

    /** Builds the Pelias autocomplete query parameters for a LocationInformationRequest. */
    public Map<String, String> toPeliasParams(OJPLocationInformationRequestStructure request) {
        var input = request.getInitialInput();
        if (input == null || input.getName() == null || input.getName().isBlank()) {
            throw new UnsupportedRequestException("Only LocationInformationRequests with InitialInput/Name are supported");
        }
        var params = new LinkedHashMap<String, String>();
        params.put("text", input.getName());
        addGeoRestriction(params, input.getGeoRestriction());
        addRestrictions(params, request.getRestrictions());
        return params;
    }

    private static void addGeoRestriction(Map<String, String> params, GeoRestrictionsStructure geo) {
        if (geo == null) {
            return;
        }
        if (geo.getRectangle() != null) {
            var upperLeft = geo.getRectangle().getUpperLeft();
            var lowerRight = geo.getRectangle().getLowerRight();
            params.put("boundary.rect.min_lat", str(lowerRight.getLatitude()));
            params.put("boundary.rect.min_lon", str(upperLeft.getLongitude()));
            params.put("boundary.rect.max_lat", str(upperLeft.getLatitude()));
            params.put("boundary.rect.max_lon", str(lowerRight.getLongitude()));
        } else if (geo.getCircle() != null) {
            var center = geo.getCircle().getCenter();
            params.put("boundary.circle.lat", str(center.getLatitude()));
            params.put("boundary.circle.lon", str(center.getLongitude()));
            // OJP radius is in metres, Pelias expects kilometres
            params.put("boundary.circle.radius", str(geo.getCircle().getRadius() / 1000.0));
        }
    }

    private static void addRestrictions(Map<String, String> params, PlaceParamStructure restrictions) {
        if (restrictions == null) {
            return;
        }
        var layers = restrictions.getType().stream()
                .map(LocationInformationMapper::peliasLayer)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!layers.isEmpty()) {
            params.put("layers", String.join(",", layers));
        }
        if (restrictions.getNumberOfResults() != null) {
            params.put("size", restrictions.getNumberOfResults().toString());
        }
        if (!restrictions.getLanguage().isEmpty()) {
            params.put("lang", restrictions.getLanguage().getFirst());
        }
        var focus = restrictions.getGeoPosition();
        if (focus != null && focus.getLatitude() != null && focus.getLongitude() != null) {
            params.put("focus.point.lat", str(focus.getLatitude()));
            params.put("focus.point.lon", str(focus.getLongitude()));
        }
    }

    private static Optional<String> peliasLayer(PlaceTypeEnumeration type) {
        return switch (type) {
            case STOP -> Optional.of("stops");
            case ADDRESS -> Optional.of("address");
            case POI -> Optional.of("venue");
            case TOPOGRAPHIC_PLACE -> Optional.of("coarse");
            case COORD, LOCATION -> Optional.empty();
        };
    }

    /** Builds the OJP LocationInformationDelivery for a Pelias response. */
    public OJPLocationInformationDeliveryStructure toDelivery(
            OJPLocationInformationRequestStructure request, PeliasResponse response) {
        var lang = Optional.ofNullable(response.geocoding())
                .map(PeliasResponse.Geocoding::query)
                .map(PeliasResponse.Query::lang)
                .map(PeliasResponse.Lang::iso6391)
                .orElse(null);
        var delivery = new OJPLocationInformationDeliveryStructure()
                .withResponseTimestamp(XmlDateTime.truncatedToMillis(ZonedDateTime.now()))
                .withStatus(true)
                .withDefaultLanguage(lang);
        if (request.getMessageIdentifier() != null) {
            delivery.withRequestMessageRef(new MessageRefStructure().withValue(request.getMessageIdentifier().getValue()));
        }
        for (var feature : response.features()) {
            var result = new PlaceResultStructure().withPlace(toPlace(feature, lang)).withComplete(true);
            delivery.getRest().add(factory.createOJPLocationInformationDeliveryStructurePlaceResult(result));
        }
        return delivery;
    }

    private static PlaceStructure toPlace(PeliasResponse.Feature feature, String lang) {
        var p = feature.properties();
        var place = new PlaceStructure()
                .withName(text(p.label() != null ? p.label() : p.name(), lang))
                .withGeoPosition(new LocationStructure()
                        .withLongitude(feature.geometry().lon())
                        .withLatitude(feature.geometry().lat()));
        switch (p.layer()) {
            case "stops" -> place.withStopPlace(new StopPlaceStructure()
                    .withStopPlaceRef(new StopPlaceRefStructure().withValue(p.id()))
                    .withStopPlaceName(text(p.name(), lang)));
            case "address" -> place.withAddress(new AddressStructure()
                    .withPublicCode(p.gid())
                    .withName(text(p.name(), lang))
                    .withStreet(p.street())
                    .withHouseNumber(p.housenumber())
                    .withPostCode(p.postalcode())
                    .withTopographicPlaceName(p.locality())
                    .withCountryName(p.country()));
            case "venue" -> place.withPointOfInterest(new PointOfInterestStructure()
                    .withPublicCode(p.gid())
                    .withName(text(p.name(), lang)));
            default -> place.withTopographicPlace(new TopographicPlaceStructure()
                    .withTopographicPlaceCode(p.gid())
                    .withTopographicPlaceName(text(p.name(), lang)));
        }
        return place;
    }

    private static InternationalTextStructure text(String value, String lang) {
        return new InternationalTextStructure()
                .withText(new NaturalLanguageStringStructure().withValue(value).withLang(lang));
    }

    private static String str(Number n) {
        return n.toString();
    }
}
