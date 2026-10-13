package io.leonard.ojp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/** The subset of a Pelias GeoJSON response that is mapped to OJP. */
@Serdeable
public record PeliasResponse(@Nullable Geocoding geocoding, List<Feature> features) {

    @Serdeable
    public record Geocoding(@Nullable Query query) {}

    @Serdeable
    public record Query(@Nullable Lang lang) {}

    @Serdeable
    public record Lang(@Nullable String iso6391) {}

    @Serdeable
    public record Feature(Geometry geometry, Properties properties) {}

    /** GeoJSON point: [lon, lat]. */
    @Serdeable
    public record Geometry(List<Double> coordinates) {
        public double lon() { return coordinates.get(0); }
        public double lat() { return coordinates.get(1); }
    }

    @Serdeable
    public record Properties(
            String id,
            String gid,
            String layer,
            String name,
            @Nullable String label,
            @Nullable String street,
            @Nullable String housenumber,
            @Nullable String postalcode,
            @Nullable String locality,
            @Nullable String country,
            @Nullable @JsonProperty("country_code") String countryCode) {}
}
