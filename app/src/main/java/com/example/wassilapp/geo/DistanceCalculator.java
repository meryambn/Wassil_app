package com.example.wassilapp.geo;

/** Offline fallback for when the Directions API cannot be reached. */
public final class DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Straight-line distance underestimates driving distance, because roads do not
     * go through buildings. In a dense city the real route is typically 25-40%
     * longer, so the result is scaled rather than reported raw — quoting the
     * as-the-crow-flies figure would systematically underprice every delivery.
     */
    private static final double ROAD_WINDING_FACTOR = 1.3;

    /** Rough city driving speed in km/h, used only when Mapbox gives no duration. */
    private static final double CITY_SPEED_KMH = 20.0;

    private DistanceCalculator() {
    }

    /** Great-circle distance in km between two points. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Estimated road distance in km, for use when Directions is unavailable. */
    public static double estimatedRoadKm(double lat1, double lng1, double lat2, double lng2) {
        return haversineKm(lat1, lng1, lat2, lng2) * ROAD_WINDING_FACTOR;
    }

    /** Minutes for a given road distance, at city speed. */
    public static int estimatedMinutes(double roadKm) {
        return (int) Math.ceil((roadKm / CITY_SPEED_KMH) * 60);
    }
}
