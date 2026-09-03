package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.CourierLocationDto;
import com.example.wassilapp.remote.dto.LocationUpsertRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST endpoints for public.courier_locations. */
public interface LocationsApi {

    /**
     * Writes the courier's current position.
     *
     * <p>"resolution=merge-duplicates" turns this POST into an upsert: because
     * order_id is the primary key, a second write for the same delivery UPDATES the
     * existing row instead of failing on a duplicate key. That is what keeps exactly
     * one current position per delivery rather than an ever-growing table.
     */
    @Headers({"Prefer: resolution=merge-duplicates", "Prefer: return=minimal"})
    @POST("rest/v1/courier_locations")
    Call<Void> upsert(@Body LocationUpsertRequest body);

    /**
     * Reads the courier's position for one order.
     *
     * <p>No ownership filter is sent, and none is needed: locations_select returns a
     * row only to the courier themselves, the sender of that order, or an admin.
     * An unauthorised caller receives an empty list, not an error.
     */
    @GET("rest/v1/courier_locations")
    Call<List<CourierLocationDto>> getForOrder(@Query("order_id") String orderIdFilter,
                                                @Query("select") String select);

    @GET("rest/v1/courier_locations")
    Call<List<CourierLocationDto>> getAllLocations(@Query("select") String select);
}
