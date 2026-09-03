package com.example.wassilapp.remote.dto;

/** PATCH body to claim a pending order (courier assigns themselves). */
public class OrderAssignRequest {
    public String delivery_id;
    public double negotiated_price;
    // Must match the order_status enum in Supabase, which in turn matches the
    // status strings the rest of the app already uses (see supabase/schema.sql).
    public String status = "prise_en_charge";

    public OrderAssignRequest(String deliveryId, double negotiatedPrice) {
        this.delivery_id = deliveryId;
        this.negotiated_price = negotiatedPrice;
    }
}
