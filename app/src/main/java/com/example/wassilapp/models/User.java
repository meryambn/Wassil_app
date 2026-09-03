package com.example.wassilapp.models;


public class User {
    private int id;
    private String fullName;
    private String phone;
    private String email;
    private String role; // "sender", "delivery", "admin"
    private double balance;
    private String vehicleType; // for delivery: "Moto", "Voiture", "Vélo"
    private double rating;
    private String wilaya;
    private String commune;
    private boolean isActive;
    private String profileImage;
    // Links this local row to its Supabase profiles.id. Null for accounts that
    // only ever existed locally. Set separately from the constructor so existing
    // call sites stay unchanged.
    private String supabaseUid;

    // Constructor
    public User(int id, String fullName, String phone, String email, String role,
                double balance, String vehicleType, double rating, String wilaya,
                String commune, boolean isActive, String profileImage) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.role = role;
        this.balance = balance;
        this.vehicleType = vehicleType;
        this.rating = rating;
        this.wilaya = wilaya;
        this.commune = commune;
        this.isActive = isActive;
        this.profileImage = profileImage;
    }

    // Getters and Setters
    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public String getVehicleType() { return vehicleType; }
    public double getRating() { return rating; }
    public String getWilaya() { return wilaya; }
    public String getCommune() { return commune; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getProfileImage() { return profileImage; }
    public String getSupabaseUid() { return supabaseUid; }
    public void setSupabaseUid(String supabaseUid) { this.supabaseUid = supabaseUid; }
}
