package com.example.wassilapp.models;


public class Payment {
    private int paymentId;
    private String orderId;
    private int payerId; // sender ID
    private String payerName;
    private int payeeId; // delivery ID
    private String payeeName;
    private double amount;
    private String currency; // "DZD"
    private String method; // "cash", "card", "cib", "edahabia"
    private String status; // "pending", "completed", "failed", "refunded"
    private long createdAt;
    private long completedAt;
    private String transactionId;
    private String notes;

    // Constructor
    public Payment(int paymentId, String orderId, int payerId, String payerName,
                   int payeeId, String payeeName, double amount, String currency,
                   String method, String status, long createdAt, long completedAt,
                   String transactionId, String notes) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.payerId = payerId;
        this.payerName = payerName;
        this.payeeId = payeeId;
        this.payeeName = payeeName;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.transactionId = transactionId;
        this.notes = notes;
    }

    // Getters and Setters
    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getPayerId() { return payerId; }
    public void setPayerId(int payerId) { this.payerId = payerId; }
    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }
    public int getPayeeId() { return payeeId; }
    public void setPayeeId(int payeeId) { this.payeeId = payeeId; }
    public String getPayeeName() { return payeeName; }
    public void setPayeeName(String payeeName) { this.payeeName = payeeName; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
