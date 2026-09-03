package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.NewOrderRequest;
import com.example.wassilapp.remote.dto.OrderAssignRequest;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.remote.dto.OrderStatusRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST endpoints for the public.orders table. */
public interface OrdersApi {

    @Headers("Prefer: return=representation")
    @POST("rest/v1/orders")
    Call<List<OrderDto>> create(@Body NewOrderRequest body);

    @GET("rest/v1/orders")
    Call<List<OrderDto>> getPending(@Query("status") String statusFilter,
                                     @Query("pickup_wilaya") String wilayaFilter,
                                     @Query("order") String order,
                                     @Query("select") String select);

    /**
     * Orders where I'm either the sender or the assigned courier.
     * The {@code or} filter mirrors what the orders_select RLS policy already
     * permits, so this returns exactly my own rows.
     */
    @GET("rest/v1/orders")
    Call<List<OrderDto>> getMyOrders(@Query("or") String orFilter,
                                      @Query("order") String order,
                                      @Query("select") String select);

    /**
     * Every order on the platform. Returns rows only for an admin — the
     * orders_select policy otherwise narrows this to the caller's own orders
     * plus anything still pending, so this is safe to call regardless.
     */
    @GET("rest/v1/orders")
    Call<List<OrderDto>> getAllOrders(@Query("order") String order,
                                       @Query("select") String select);

    // Filtering on status=pending + delivery_id=is.null makes this a safe "claim":
    // if another courier already grabbed it, zero rows match and the update no-ops.
    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/orders")
    Call<List<OrderDto>> claim(@Query("id") String idFilter,
                                @Query("status") String statusFilter,
                                @Query("delivery_id") String deliveryIdFilter,
                                @Body OrderAssignRequest body);

    /**
     * Status transition on a single order. RLS (orders_update) already restricts
     * this to the order's sender, its assigned courier, or an admin.
     */
    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/orders")
    Call<List<OrderDto>> updateStatus(@Query("id") String idFilter,
                                       @Body OrderStatusRequest body);
}
