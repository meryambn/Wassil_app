package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.AcceptOfferRequest;
import com.example.wassilapp.remote.dto.NewOfferRequest;
import com.example.wassilapp.remote.dto.OfferDto;
import com.example.wassilapp.remote.dto.OrderDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Thin wrapper over {@link OffersApi}. Callbacks land on the main thread. */
public class OfferRepository {

    public interface OfferCallback {
        void onSuccess(OfferDto offer);

        void onError(String message);
    }

    public interface OfferListCallback {
        void onSuccess(List<OfferDto> offers);

        void onError(String message);
    }

    public interface AcceptCallback {
        void onAccepted(OrderDto order);

        void onError(String message);
    }

    private static final String SELECT_WITH_COURIER =
            "*,courier:profiles!courier_id(id,full_name,phone,vehicle_type,rating,rating_count)";

    private final OffersApi api = SupabaseClient.restClient().create(OffersApi.class);

    public void makeOffer(String orderId, String courierUid, double price, String message,
                           OfferCallback callback) {
        api.create(new NewOfferRequest(orderId, courierUid, price, message))
                .enqueue(new Callback<List<OfferDto>>() {
                    @Override
                    public void onResponse(Call<List<OfferDto>> call, Response<List<OfferDto>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OfferDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void getPendingOffers(String orderId, OfferListCallback callback) {
        api.getForOrder("eq." + orderId, "eq.pending", "offered_price.asc", SELECT_WITH_COURIER)
                .enqueue(new Callback<List<OfferDto>>() {
                    @Override
                    public void onResponse(Call<List<OfferDto>> call, Response<List<OfferDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OfferDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void acceptOffer(String offerId, AcceptCallback callback) {
        api.acceptOffer(new AcceptOfferRequest(offerId)).enqueue(new Callback<OrderDto>() {
            @Override
            public void onResponse(Call<OrderDto> call, Response<OrderDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onAccepted(response.body());
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<OrderDto> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    private String errorMessage(Response<?> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : "Request failed";
        } catch (Exception e) {
            return "Request failed (" + response.code() + ")";
        }
    }
}
