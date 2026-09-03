package com.example.wassilapp.remote.dto;

/** Mirrors a row of the public.orders table. */
public class OrderDto {
    public String id;
    public String sender_id;
    public String delivery_id;
    public String status;
    public double asking_price;
    public Double negotiated_price;
    public String pickup_address;
    public String drop_address;
    public String pickup_wilaya;
    public String drop_wilaya;
    public Double pickup_lat;
    public Double pickup_lng;
    public Double drop_lat;
    public Double drop_lng;
    public Double distance_km;
    public Integer estimated_minutes;
    public String package_type;
    public Double weight_kg;
    public boolean is_urgent;
    public String special_instructions;
    public String created_at;
    public String picked_up_at;
    public String delivered_at;

    /**
     * Assigned courier, pulled in the same request via PostgREST resource embedding
     * (select=*,delivery:profiles!delivery_id(...)). orders has two FKs to profiles,
     * so the !delivery_id hint is required to disambiguate. Null when unassigned.
     */
    public ProfileRef delivery;

    /**
     * The sender, embedded the same way. Readable by the assigned courier thanks to
     * the shares_order_with() clause in the profiles_select policy; without that a
     * courier could not see who they are delivering for.
     */
    public ProfileRef sender;

    public static class ProfileRef {
        public String id;
        public String full_name;
        public String phone;
        public String vehicle_type;
        public double rating;
        /** How many reviews that average is built on. 0 means never rated. */
        public int rating_count;
    }
}
