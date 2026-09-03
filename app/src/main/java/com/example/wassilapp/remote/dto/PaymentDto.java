package com.example.wassilapp.remote.dto;

/** Mirrors a row of public.payments. */
public class PaymentDto {
    public String id;
    public String order_id;
    public String payer_id;
    public String payee_id;
    public double amount;
    public String currency;
    /** cash | wallet | cib | edahabia */
    public String method;
    /** pending | completed | failed | refunded */
    public String status;
    public String transaction_id;
    public String notes;
    public String created_at;
    public String completed_at;
}
