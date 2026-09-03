package com.example.wassilapp.geo.dto;

import java.util.List;

/**
 * Mapbox Directions v5 response.
 *
 * <p>Only the fields the app uses are declared; Gson ignores the rest. The route
 * geometry is requested only when something is going to draw it — the price
 * estimate asks for overview=false, which keeps that array out of the payload
 * entirely rather than downloading a polyline nobody looks at.
 */
public class DirectionsResponse {
    public String code;
    public List<Route> routes;

    public static class Route {
        /** Metres along the road, not a straight line. */
        public double distance;
        /** Seconds, from Mapbox's driving profile. */
        public double duration;
        /** Present only when overview != false. */
        public Geometry geometry;
    }

    /**
     * A GeoJSON LineString as Mapbox returns it.
     *
     * <p>Each entry is [longitude, latitude] — in that order. GeoJSON puts longitude
     * first, which is the opposite of how coordinates are usually spoken, and getting
     * it backwards draws a line through the wrong hemisphere rather than raising an
     * error.
     */
    public static class Geometry {
        public String type;
        public List<List<Double>> coordinates;
    }

    public boolean hasRoute() {
        return "Ok".equals(code) && routes != null && !routes.isEmpty();
    }
}
