package com.example.wassilapp.remote.dto;

/** Body for the payment RPCs, which take only the order they concern. */
public class OrderIdRequest {
    public String p_order_id;

    public OrderIdRequest(String orderId) {
        this.p_order_id = orderId;
    }
}
