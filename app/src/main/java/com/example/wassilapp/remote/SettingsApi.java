package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.PlatformSettingDto;
import com.example.wassilapp.remote.dto.SetCommissionRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Supabase endpoints for platform configuration settings.
 */
public interface SettingsApi {

    @GET("rest/v1/platform_settings")
    Call<List<PlatformSettingDto>> getSettings(@Query("key") String keyFilter);

    @POST("rest/v1/rpc/set_platform_commission")
    Call<Double> setCommission(@Body SetCommissionRequest body);
}
