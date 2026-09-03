package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.NewOrderRequest;
import com.example.wassilapp.remote.dto.OrderAssignRequest;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.remote.dto.OrderStatusRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Thin wrapper over {@link OrdersApi} for Activities. Callbacks land on the main thread. */
public class OrderRepository {

    public interface OrderCallback {
        void onSuccess(OrderDto order);

        void onError(String message);
    }

    public interface OrderListCallback {
        void onSuccess(List<OrderDto> orders);

        void onError(String message);
    }

    public interface ClaimCallback {
        void onClaimed(OrderDto order);

        void onAlreadyTaken();

        void onError(String message);
    }

    /**
     * Pulls the assigned courier's profile in the same round trip. orders has two
     * FKs to profiles, so the !delivery_id hint is required or PostgREST returns
     * PGRST201 (ambiguous relationship).
     */
    private static final String SELECT_WITH_COURIER =
            "*,delivery:profiles!delivery_id(id,full_name,phone,vehicle_type,rating)";

    /** Both counterparties, for screens that need to show who's on the other side. */
    private static final String SELECT_WITH_BOTH_PARTIES =
            "*,delivery:profiles!delivery_id(id,full_name,phone,vehicle_type,rating)"
                    + ",sender:profiles!sender_id(id,full_name,phone,vehicle_type,rating)";

    private final OrdersApi api = SupabaseClient.restClient().create(OrdersApi.class);

    public void create(NewOrderRequest request, OrderCallback callback) {
        api.create(request).enqueue(new Callback<List<OrderDto>>() {
            @Override
            public void onResponse(Call<List<OrderDto>> call, Response<List<OrderDto>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<OrderDto>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    public void getPending(String wilaya, OrderListCallback callback) {
        api.getPending("eq.pending", "eq." + wilaya, "created_at.desc", "*")
                .enqueue(new Callback<List<OrderDto>>() {
                    @Override
                    public void onResponse(Call<List<OrderDto>> call, Response<List<OrderDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OrderDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    /** Orders where I'm the sender or the assigned courier, newest first. */
    public void getMyOrders(String myUid, OrderListCallback callback) {
        String orFilter = "(sender_id.eq." + myUid + ",delivery_id.eq." + myUid + ")";
        api.getMyOrders(orFilter, "created_at.desc", SELECT_WITH_BOTH_PARTIES)
                .enqueue(new Callback<List<OrderDto>>() {
                    @Override
                    public void onResponse(Call<List<OrderDto>> call, Response<List<OrderDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OrderDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    /** Admin-only in practice; RLS narrows the result set for everyone else. */
    public void getAllOrders(OrderListCallback callback) {
        api.getAllOrders("created_at.desc", SELECT_WITH_BOTH_PARTIES)
                .enqueue(new Callback<List<OrderDto>>() {
                    @Override
                    public void onResponse(Call<List<OrderDto>> call, Response<List<OrderDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OrderDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void updateStatus(String orderId, String newStatus, OrderCallback callback) {
        api.updateStatus("eq." + orderId, new OrderStatusRequest(newStatus))
                .enqueue(new Callback<List<OrderDto>>() {
                    @Override
                    public void onResponse(Call<List<OrderDto>> call, Response<List<OrderDto>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else if (response.isSuccessful()) {
                            // 0 rows matched: RLS rejected it, or the order isn't in the cloud.
                            callback.onError("Commande introuvable ou non autorisée");
                        } else {
                            callback.onError(errorMessage(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OrderDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    public void claim(String orderId, String courierId, double negotiatedPrice, ClaimCallback callback) {
        api.claim("eq." + orderId, "eq.pending", "is.null", new OrderAssignRequest(courierId, negotiatedPrice))
                .enqueue(new Callback<List<OrderDto>>() {
                    @Override
                    public void onResponse(Call<List<OrderDto>> call, Response<List<OrderDto>> response) {
                        if (!response.isSuccessful()) {
                            callback.onError(errorMessage(response));
                        } else if (response.body() == null || response.body().isEmpty()) {
                            callback.onAlreadyTaken();
                        } else {
                            callback.onClaimed(response.body().get(0));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<OrderDto>> call, Throwable t) {
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
