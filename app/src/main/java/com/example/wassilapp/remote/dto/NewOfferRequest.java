package com.example.wassilapp.remote.dto;

/** Body for POST rest/v1/offers — a courier proposing a price. */
public class NewOfferRequest {
    public String order_id;
    public String courier_id;
    public double offered_price;
    public String message;

    public NewOfferRequest(String orderId, String courierId, double offeredPrice, String message) {
        this.order_id = orderId;
        this.courier_id = courierId;
        this.offered_price = offeredPrice;
        this.message = (message == null || message.isEmpty()) ? null : message;
    }
}
