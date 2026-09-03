package com.example.wassilapp.models;

public class Order {
    private String orderId;
    private int senderId;
    private String senderName;
    private int deliveryId;
    private String deliveryName;
    private String status; // "pending", "prise_en_charge", "en_route", "livre", "annule"
    private double price;
    private double negotiatedPrice;
    private String pickupAddress;
    private String dropAddress;
    private String pickupWilaya;
    private String dropWilaya;
    private double distance;
    private int estimatedTime;
    private String createdAt;
    private String pickedUpAt;
    private String deliveredAt;
    private double senderLatitude;
    private double senderLongitude;
    private double deliveryLatitude;
    private double deliveryLongitude;
    private String packageType;
    private double weight;
    private String specialInstructions;

    // Full Constructor
    public Order(String orderId, int senderId, String senderName, int deliveryId,
                 String deliveryName, String status, double price, double negotiatedPrice,
                 String pickupAddress, String dropAddress, String pickupWilaya,
                 String dropWilaya, double distance, int estimatedTime, String createdAt,
                 String pickedUpAt, String deliveredAt, double senderLatitude,
                 double senderLongitude, double deliveryLatitude, double deliveryLongitude,
                 String packageType, double weight, String specialInstructions) {
        this.orderId = orderId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.deliveryId = deliveryId;
        this.deliveryName = deliveryName;
        this.status = status;
        this.price = price;
        this.negotiatedPrice = negotiatedPrice;
        this.pickupAddress = pickupAddress;
        this.dropAddress = dropAddress;
        this.pickupWilaya = pickupWilaya;
        this.dropWilaya = dropWilaya;
        this.distance = distance;
        this.estimatedTime = estimatedTime;
        this.createdAt = createdAt;
        this.pickedUpAt = pickedUpAt;
        this.deliveredAt = deliveredAt;
        this.senderLatitude = senderLatitude;
        this.senderLongitude = senderLongitude;
        this.deliveryLatitude = deliveryLatitude;
        this.deliveryLongitude = deliveryLongitude;
        this.packageType = packageType;
        this.weight = weight;
        this.specialInstructions = specialInstructions;
    }

    // Simplified constructor for creating new orders
    public Order(int senderId, String senderName, String pickupAddress, String dropAddress,
                 String pickupWilaya, String dropWilaya, double distance, int estimatedTime,
                 String packageType, double weight, String specialInstructions) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.pickupAddress = pickupAddress;
        this.dropAddress = dropAddress;
        this.pickupWilaya = pickupWilaya;
        this.dropWilaya = dropWilaya;
        this.distance = distance;
        this.estimatedTime = estimatedTime;
        this.packageType = packageType;
        this.weight = weight;
        this.specialInstructions = specialInstructions;
        this.status = "pending";
        this.deliveryId = 0;
        this.deliveryName = "—";
        this.price = 0;
        this.negotiatedPrice = 0;
    }

    // Getters
    public String getOrderId() { return orderId; }
    public int getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public int getDeliveryId() { return deliveryId; }
    public String getDeliveryName() { return deliveryName; }
    public String getStatus() { return status; }
    public double getPrice() { return price; }
    public double getNegotiatedPrice() { return negotiatedPrice; }
    public String getPickupAddress() { return pickupAddress; }
    public String getDropAddress() { return dropAddress; }
    public String getPickupWilaya() { return pickupWilaya; }
    public String getDropWilaya() { return dropWilaya; }
    public double getDistance() { return distance; }
    public int getEstimatedTime() { return estimatedTime; }
    public String getCreatedAt() { return createdAt; }
    public String getPickedUpAt() { return pickedUpAt; }
    public String getDeliveredAt() { return deliveredAt; }
    public double getSenderLatitude() { return senderLatitude; }
    public double getSenderLongitude() { return senderLongitude; }
    public double getDeliveryLatitude() { return deliveryLatitude; }
    public double getDeliveryLongitude() { return deliveryLongitude; }
    public String getPackageType() { return packageType; }
    public double getWeight() { return weight; }
    public String getSpecialInstructions() { return specialInstructions; }

    // Setters
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setDeliveryId(int deliveryId) { this.deliveryId = deliveryId; }
    public void setDeliveryName(String deliveryName) { this.deliveryName = deliveryName; }
    public void setStatus(String status) { this.status = status; }
    public void setPrice(double price) { this.price = price; }
    public void setNegotiatedPrice(double negotiatedPrice) { this.negotiatedPrice = negotiatedPrice; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public void setDropAddress(String dropAddress) { this.dropAddress = dropAddress; }
    public void setPickupWilaya(String pickupWilaya) { this.pickupWilaya = pickupWilaya; }
    public void setDropWilaya(String dropWilaya) { this.dropWilaya = dropWilaya; }
    public void setDistance(double distance) { this.distance = distance; }
    public void setEstimatedTime(int estimatedTime) { this.estimatedTime = estimatedTime; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setPickedUpAt(String pickedUpAt) { this.pickedUpAt = pickedUpAt; }
    public void setDeliveredAt(String deliveredAt) { this.deliveredAt = deliveredAt; }
    public void setSenderLatitude(double senderLatitude) { this.senderLatitude = senderLatitude; }
    public void setSenderLongitude(double senderLongitude) { this.senderLongitude = senderLongitude; }
    public void setDeliveryLatitude(double deliveryLatitude) { this.deliveryLatitude = deliveryLatitude; }
    public void setDeliveryLongitude(double deliveryLongitude) { this.deliveryLongitude = deliveryLongitude; }
    public void setPackageType(String packageType) { this.packageType = packageType; }
    public void setWeight(double weight) { this.weight = weight; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
}