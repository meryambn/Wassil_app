package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.CourierLocationDto;
import com.example.wassilapp.remote.dto.LocationUpsertRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The only door to courier position data.
 *
 * <p>Neither the tracking service nor the tracking screen talks to Supabase
 * directly — they ask this class. That is the same rule used for orders, offers and
 * withdrawals: the layer that knows about HTTP is separate from the layer that knows
 * about GPS or about drawing a map.
 */
public class LocationRepository {

    public interface LocationCallback {
        void onSuccess(CourierLocationDto location);

        /** No position recorded yet — the courier has not started moving. */
        void onNotFound();

        void onError(String message);
    }

    private final LocationsApi api = SupabaseClient.restClient().create(LocationsApi.class);

    /**
     * Publishes the courier's current position. Fire-and-forget by design: a lost
     * ping is not worth retrying because a fresher one follows within seconds, and
     * queueing stale positions would only waste battery and bandwidth.
     */
    public void publish(String orderId, String courierUid, double lat, double lng) {
        api.upsert(new LocationUpsertRequest(orderId, courierUid, lat, lng))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        // Nothing to do; the next fix will correct any gap.
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        // Offline: silently skip this ping rather than crash the service.
                    }
                });
    }

    public void getForOrder(String orderId, LocationCallback callback) {
        api.getForOrder("eq." + orderId, "*").enqueue(new Callback<List<CourierLocationDto>>() {
            @Override
            public void onResponse(Call<List<CourierLocationDto>> call,
                                    Response<List<CourierLocationDto>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Position indisponible");
                } else if (response.body().isEmpty()) {
                    // Either no ping yet, or RLS filtered it out because this user is
                    // not party to the order. Both look the same to the client — by design.
                    callback.onNotFound();
                } else {
                    callback.onSuccess(response.body().get(0));
                }
            }

            @Override
            public void onFailure(Call<List<CourierLocationDto>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Erreur réseau");
            }
        });
    }

    public interface AllLocationsCallback {
        void onSuccess(List<CourierLocationDto> locations);

        void onError(String message);
    }

    public void getAllLocations(AllLocationsCallback callback) {
        api.getAllLocations("*").enqueue(new Callback<List<CourierLocationDto>>() {
            @Override
            public void onResponse(Call<List<CourierLocationDto>> call,
                                    Response<List<CourierLocationDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Positions indisponibles");
                }
            }

            @Override
            public void onFailure(Call<List<CourierLocationDto>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Erreur réseau");
            }
        });
    }
}
