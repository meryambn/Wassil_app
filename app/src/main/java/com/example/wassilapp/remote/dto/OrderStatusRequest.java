package com.example.wassilapp.remote.dto;

/**
 * PATCH body for an order status transition. Values must be members of the
 * order_status enum in Supabase (pending, prise_en_charge, en_route, livre, annule).
 */
public class OrderStatusRequest {
    public String status;

    public OrderStatusRequest(String status) {
        this.status = status;
    }
}
