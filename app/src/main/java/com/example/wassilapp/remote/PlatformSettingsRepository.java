package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.PlatformSettingDto;
import com.example.wassilapp.remote.dto.SetCommissionRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Handles platform global configuration (such as commission percentage)
 * with Supabase remote persistence and fallback defaults.
 */
public class PlatformSettingsRepository {

    public static final double DEFAULT_COMMISSION_PERCENTAGE = 15.0;

    public interface RateCallback {
        void onRateLoaded(double percentage);
        void onError(String message);
    }

    public interface SetRateCallback {
        void onRateSaved(double percentage);
        void onError(String message);
    }

    private final SettingsApi api = SupabaseClient.restClient().create(SettingsApi.class);

    /**
     * Reads the current commission percentage from platform_settings.
     */
    public void getCommissionRate(RateCallback callback) {
        api.getSettings("eq.commission_percentage").enqueue(new Callback<List<PlatformSettingDto>>() {
            @Override
            public void onResponse(Call<List<PlatformSettingDto>> call, Response<List<PlatformSettingDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    try {
                        double rate = Double.parseDouble(response.body().get(0).value);
                        callback.onRateLoaded(rate);
                        return;
                    } catch (Exception ignored) {}
                }
                // Fallback to default
                callback.onRateLoaded(DEFAULT_COMMISSION_PERCENTAGE);
            }

            @Override
            public void onFailure(Call<List<PlatformSettingDto>> call, Throwable t) {
                callback.onRateLoaded(DEFAULT_COMMISSION_PERCENTAGE);
            }
        });
    }

    /**
     * Updates the commission percentage via set_platform_commission RPC.
     */
    public void setCommissionRate(double percentage, SetRateCallback callback) {
        if (percentage < 0 || percentage > 100) {
            callback.onError("Le pourcentage doit être compris entre 0% et 100%");
            return;
        }

        api.setCommission(new SetCommissionRequest(percentage)).enqueue(new Callback<Double>() {
            @Override
            public void onResponse(Call<Double> call, Response<Double> response) {
                if (response.isSuccessful()) {
                    callback.onRateSaved(percentage);
                } else {
                    callback.onError("Erreur serveur : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Double> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Erreur de connexion");
            }
        });
    }
}
