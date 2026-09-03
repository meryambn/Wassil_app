package com.example.wassilapp.remote.dto;

/** One row of public.courier_locations — the courier's latest position for an order. */
public class CourierLocationDto {
    public String order_id;
    public String courier_id;
    public double lat;
    public double lng;
    public String updated_at;
}
