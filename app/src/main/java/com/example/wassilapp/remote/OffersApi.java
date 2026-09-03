package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.AcceptOfferRequest;
import com.example.wassilapp.remote.dto.NewOfferRequest;
import com.example.wassilapp.remote.dto.OfferDto;
import com.example.wassilapp.remote.dto.OrderDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST endpoints for public.offers. */
public interface OffersApi {

    @Headers("Prefer: return=representation")
    @POST("rest/v1/offers")
    Call<List<OfferDto>> create(@Body NewOfferRequest body);

    /** Offers on one order, courier profile embedded so the sender can compare bidders. */
    @GET("rest/v1/offers")
    Call<List<OfferDto>> getForOrder(@Query("order_id") String orderIdFilter,
                                      @Query("status") String statusFilter,
                                      @Query("order") String order,
                                      @Query("select") String select);

    /**
     * Assignment happens server-side in one transaction — see the accept_offer
     * function. Doing it as separate PATCHes from the client would leave a window
     * where two offers could both be accepted.
     */
    @POST("rest/v1/rpc/accept_offer")
    Call<OrderDto> acceptOffer(@Body AcceptOfferRequest body);
}
