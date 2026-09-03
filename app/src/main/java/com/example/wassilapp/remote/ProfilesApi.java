package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.NewProfileRequest;
import com.example.wassilapp.remote.dto.Profile;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** PostgREST endpoints for the public.profiles table. */
public interface ProfilesApi {

    @GET("rest/v1/profiles")
    Call<List<Profile>> getById(@Query("id") String idFilter, @Query("select") String select);

    @GET("rest/v1/profiles")
    Call<List<Profile>> getCouriers(@Query("role") String roleFilter,
                                    @Query("order") String order,
                                    @Query("select") String select);

    @Headers("Prefer: return=representation")
    @POST("rest/v1/profiles")
    Call<List<Profile>> create(@Body NewProfileRequest body);
}
