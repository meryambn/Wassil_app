package com.example.wassilapp.geo.dto;

import java.util.List;

/**
 * Mapbox Geocoding v5 response, used here only in reverse (coordinates -> place).
 *
 * <p>The wilaya is not a top-level field: Mapbox returns the administrative
 * hierarchy in each feature's context array, where the entry whose id begins with
 * "region" is what Algeria calls a wilaya.
 */
public class GeocodeResponse {
    public List<Feature> features;

    public static class Feature {
        /** Full label, e.g. "12 Rue Didouche Mourad, Alger, Algerie". */
        public String place_name;
        public String text;
        /** Coordinates in [longitude, latitude] order from Mapbox. */
        public List<Double> center;
        public List<Context> context;
    }

    public static class Context {
        /** Prefixed by type: "region.123", "place.456", "country.7". */
        public String id;
        public String text;
    }

    public Feature first() {
        return (features == null || features.isEmpty()) ? null : features.get(0);
    }
}
