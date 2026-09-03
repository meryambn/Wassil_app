package com.example.wassilapp.remote.dto;

/** A courier's price proposal on a pending order (public.offers). */
public class OfferDto {
    public String id;
    public String order_id;
    public String courier_id;
    public double offered_price;
    public String message;
    public String status; // pending | accepted | rejected | withdrawn
    public String created_at;

    /** Courier profile, embedded via offers -> profiles FK so the sender sees who bid. */
    public OrderDto.ProfileRef courier;
}
