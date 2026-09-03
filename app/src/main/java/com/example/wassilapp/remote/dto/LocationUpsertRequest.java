package com.example.wassilapp.remote.dto;

/**
 * Body for writing the courier's current position.
 *
 * <p>courier_id is sent explicitly because it is part of the row, but it is not
 * trusted: the locations_upsert policy enforces {@code courier_id = auth.uid()},
 * so a client that puts someone else's id here is rejected by the database
 * (verified: 42501 row-level security violation).
 */
public class LocationUpsertRequest {
    public String order_id;
    public String courier_id;
    public double lat;
    public double lng;

    public LocationUpsertRequest(String orderId, String courierId, double lat, double lng) {
        this.order_id = orderId;
        this.courier_id = courierId;
        this.lat = lat;
        this.lng = lng;
    }
}
