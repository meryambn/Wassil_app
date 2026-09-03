package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.NewRatingRequest;
import com.example.wassilapp.remote.dto.RatingDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST endpoints for public.ratings. */
public interface RatingsApi {

    @Headers("Prefer: return=representation")
    @POST("rest/v1/ratings")
    Call<List<RatingDto>> create(@Body NewRatingRequest body);

    /**
     * Whether this user already reviewed this order.
     *
     * <p>Used only to decide whether to offer the form. The real guarantee is the
     * unique (order_id, rater_id) constraint, which rejects a duplicate even if two
     * devices submit at the same moment and both saw an empty result here.
     */
    @GET("rest/v1/ratings")
    Call<List<RatingDto>> getMineForOrder(@Query("order_id") String orderIdFilter,
                                          @Query("rater_id") String raterFilter);
}
