package com.example.wassilapp.remote.dto;

/**
 * Insert-only body for POST rest/v1/profiles.
 * Fields left null are omitted by Gson so Postgres applies its column defaults
 * (e.g. balance, rating) instead of receiving an explicit NULL.
 */
public class NewProfileRequest {
    public String id; // must equal the authenticated auth.uid()
    public String full_name;
    public String phone;
    public String role;
    public String wilaya;
    public String commune;
    public String vehicle_type;

    public NewProfileRequest(String id, String fullName, String phone, String role,
                              String wilaya, String commune, String vehicleType) {
        this.id = id;
        this.full_name = fullName;
        this.phone = phone;
        this.role = role;
        this.wilaya = wilaya;
        this.commune = commune;
        this.vehicle_type = (vehicleType == null || vehicleType.isEmpty()) ? null : vehicleType;
    }
}
