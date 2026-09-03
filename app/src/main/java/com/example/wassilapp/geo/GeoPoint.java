package com.example.wassilapp.geo;

/**
 * A location on the map, plus whatever we know about it in words.
 *
 * <p>Immutable on purpose: a picked address flows from the picker through the order
 * form into the insert payload, and a value that could be edited halfway would let
 * the coordinates and the label drift apart.
 */
public class GeoPoint {
    public final double lat;
    public final double lng;
    /** Human-readable label, from reverse geocoding or typed by the user. */
    public final String address;
    /** Wilaya name, lowercased to match how the column is already stored. */
    public final String wilaya;

    public GeoPoint(double lat, double lng, String address, String wilaya) {
        this.lat = lat;
        this.lng = lng;
        this.address = address;
        this.wilaya = wilaya;
    }

    public boolean isValid() {
        return lat != 0 || lng != 0;
    }
}
