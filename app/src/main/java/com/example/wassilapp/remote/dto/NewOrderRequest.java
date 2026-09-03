package com.example.wassilapp.remote.dto;

/** Insert-only body for POST rest/v1/orders. id/status/created_at use column defaults. */
public class NewOrderRequest {
    public String sender_id;
    public double asking_price;
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

    public NewOrderRequest(String senderId, double askingPrice, String pickupAddress, String dropAddress,
                            String pickupWilaya, String dropWilaya,
                            Double pickupLat, Double pickupLng, Double dropLat, Double dropLng,
                            double distanceKm, int estimatedMinutes,
                            String packageType, double weightKg, boolean isUrgent, String specialInstructions) {
        this.sender_id = senderId;
        this.asking_price = askingPrice;
        this.pickup_address = pickupAddress;
        this.drop_address = dropAddress;
        this.pickup_wilaya = pickupWilaya;
        this.drop_wilaya = dropWilaya;
        this.pickup_lat = pickupLat;
        this.pickup_lng = pickupLng;
        this.drop_lat = dropLat;
        this.drop_lng = dropLng;
        this.distance_km = distanceKm;
        this.estimated_minutes = estimatedMinutes;
        this.package_type = packageType;
        this.weight_kg = weightKg;
        this.is_urgent = isUrgent;
        this.special_instructions = (specialInstructions == null || specialInstructions.isEmpty()) ? null : specialInstructions;
    }
}
