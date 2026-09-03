package com.example.wassilapp.remote.dto;

/** Mirrors a row of the public.profiles table. */
public class Profile {
    public String id;
    public String full_name;
    public String phone;
    public String role; // "sender", "delivery", "admin"
    public double balance;
    public String vehicle_type;
    public double rating;
    /** How many reviews that average is built on. 0 means never rated. */
    public int rating_count;
    public String wilaya;
    public String commune;
    public boolean is_active;
    public String profile_image_url;
    public String kyc_status;
    public String created_at;
}
