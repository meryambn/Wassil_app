package com.example.wassilapp.remote.dto;

/** Body for the accept_offer Postgres function (POST rest/v1/rpc/accept_offer). */
public class AcceptOfferRequest {
    public String p_offer_id;

    public AcceptOfferRequest(String offerId) {
        this.p_offer_id = offerId;
    }
}
