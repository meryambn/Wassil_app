package com.example.wassilapp.models;

/**
 * Message model representing user and assistant chat items, including structured action cards.
 */
public class AiMessage {

    public static final String ACTION_TRACK_ORDER = "TRACK_ORDER";
    public static final String ACTION_CREATE_DELIVERY = "CREATE_DELIVERY";
    public static final String ACTION_OPEN_KYC = "OPEN_KYC";
    public static final String ACTION_OPEN_WITHDRAWALS = "OPEN_WITHDRAWALS";

    private final String id;
    private final String text;
    private final boolean isUser;
    private final long timestamp;

    // Structured Action Card attributes
    private boolean hasActionCard;
    private String actionType;
    private String cardTitle;
    private String cardSubtitle;
    private String cardBadge;
    private String cardButtonText;

    // Structured action parameters (no natural-language parsing required on click)
    private String actionOrderId;
    private String prefillPickup;
    private String prefillDrop;
    private double prefillWeight;
    private String prefillPackageType = "Colis";
    private boolean prefillUrgent;
    private String prefillDimensions;

    public AiMessage(String text, boolean isUser) {
        this.id = String.valueOf(System.currentTimeMillis()) + "-" + (isUser ? "u" : "a");
        this.text = text;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
        this.hasActionCard = false;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean hasActionCard() {
        return hasActionCard;
    }

    public void setHasActionCard(boolean hasActionCard) {
        this.hasActionCard = hasActionCard;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getCardTitle() {
        return cardTitle;
    }

    public void setCardTitle(String cardTitle) {
        this.cardTitle = cardTitle;
    }

    public String getCardSubtitle() {
        return cardSubtitle;
    }

    public void setCardSubtitle(String cardSubtitle) {
        this.cardSubtitle = cardSubtitle;
    }

    public String getCardBadge() {
        return cardBadge;
    }

    public void setCardBadge(String cardBadge) {
        this.cardBadge = cardBadge;
    }

    public String getCardButtonText() {
        return cardButtonText;
    }

    public void setCardButtonText(String cardButtonText) {
        this.cardButtonText = cardButtonText;
    }

    public String getActionOrderId() {
        return actionOrderId;
    }

    public void setActionOrderId(String actionOrderId) {
        this.actionOrderId = actionOrderId;
    }

    public String getPrefillPickup() {
        return prefillPickup;
    }

    public void setPrefillPickup(String prefillPickup) {
        this.prefillPickup = prefillPickup;
    }

    public String getPrefillDrop() {
        return prefillDrop;
    }

    public void setPrefillDrop(String prefillDrop) {
        this.prefillDrop = prefillDrop;
    }

    public double getPrefillWeight() {
        return prefillWeight;
    }

    public void setPrefillWeight(double prefillWeight) {
        this.prefillWeight = prefillWeight;
    }

    public String getPrefillPackageType() {
        return prefillPackageType;
    }

    public void setPrefillPackageType(String prefillPackageType) {
        this.prefillPackageType = prefillPackageType;
    }

    public boolean isPrefillUrgent() {
        return prefillUrgent;
    }

    public void setPrefillUrgent(boolean prefillUrgent) {
        this.prefillUrgent = prefillUrgent;
    }

    public String getPrefillDimensions() {
        return prefillDimensions;
    }

    public void setPrefillDimensions(String prefillDimensions) {
        this.prefillDimensions = prefillDimensions;
    }
}
