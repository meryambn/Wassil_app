package com.example.wassilapp.ai;

/**
 * Maintains session-level conversation state and slot-filling context.
 * Enables multi-turn slot filling without forcing the user to repeat already provided parameters.
 */
public class ConversationState {

    private AiIntent activeIntent = AiIntent.UNKNOWN;

    // Slots for Price Estimation
    private String originWilaya;
    private String destinationWilaya;
    private Double weightKg;
    private String packageType = "Colis";
    private boolean isUrgent = false;
    private Double lengthCm;
    private Double widthCm;
    private Double heightCm;

    // Slot for Order Tracking
    private String targetOrderId;

    public AiIntent getActiveIntent() {
        return activeIntent;
    }

    public void setActiveIntent(AiIntent activeIntent) {
        this.activeIntent = activeIntent;
    }

    public String getOriginWilaya() {
        return originWilaya;
    }

    public void setOriginWilaya(String originWilaya) {
        if (originWilaya != null && !originWilaya.trim().isEmpty()) {
            this.originWilaya = originWilaya.trim();
        }
    }

    public String getDestinationWilaya() {
        return destinationWilaya;
    }

    public void setDestinationWilaya(String destinationWilaya) {
        if (destinationWilaya != null && !destinationWilaya.trim().isEmpty()) {
            this.destinationWilaya = destinationWilaya.trim();
        }
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        if (weightKg != null && weightKg > 0) {
            this.weightKg = weightKg;
        }
    }

    public String getPackageType() {
        return packageType != null ? packageType : "Colis";
    }

    public void setPackageType(String packageType) {
        if (packageType != null && !packageType.trim().isEmpty()) {
            this.packageType = packageType.trim();
        }
    }

    public boolean isUrgent() {
        return isUrgent;
    }

    public void setUrgent(boolean urgent) {
        isUrgent = urgent;
    }

    public Double getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(Double lengthCm) {
        this.lengthCm = lengthCm;
    }

    public Double getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(Double widthCm) {
        this.widthCm = widthCm;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Double heightCm) {
        this.heightCm = heightCm;
    }

    public String getTargetOrderId() {
        return targetOrderId;
    }

    public void setTargetOrderId(String targetOrderId) {
        this.targetOrderId = targetOrderId;
    }

    /**
     * Checks if minimum slots required for price estimation are satisfied.
     */
    public boolean hasMinimumPriceSlots() {
        return originWilaya != null && !originWilaya.isEmpty()
                && destinationWilaya != null && !destinationWilaya.isEmpty()
                && weightKg != null && weightKg > 0;
    }

    /**
     * Resets price estimation slots once an estimate has been delivered.
     */
    public void clearPriceSlots() {
        originWilaya = null;
        destinationWilaya = null;
        weightKg = null;
        packageType = "Colis";
        isUrgent = false;
        lengthCm = null;
        widthCm = null;
        heightCm = null;
        if (activeIntent == AiIntent.PRICE_ESTIMATE) {
            activeIntent = AiIntent.UNKNOWN;
        }
    }

    /**
     * Complete reset of conversation context.
     */
    public void reset() {
        clearPriceSlots();
        targetOrderId = null;
        activeIntent = AiIntent.UNKNOWN;
    }
}
