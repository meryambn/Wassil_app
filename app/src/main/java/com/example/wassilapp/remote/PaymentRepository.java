package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.OrderIdRequest;
import com.example.wassilapp.remote.dto.PaymentDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Records that money changed hands.
 *
 * <p>Neither method here sends an amount. The order already knows what it costs, and
 * letting the client name the figure it is paying is the same mistake that made
 * negotiated_price editable — the functions read it server-side instead.
 *
 * <p>There is no insert path: authenticated holds SELECT on payments and nothing
 * more, so every write goes through a function that re-checks who is asking.
 */
public class PaymentRepository {

    public interface PaymentCallback {
        void onSuccess(PaymentDto payment);

        void onError(String message);
    }

    public interface LookupCallback {
        /** @param payment the recorded payment for this order, or null if unpaid. */
        void onResult(PaymentDto payment);

        void onError(String message);
    }

    private interface Api {
        /** Debits the sender's in-app balance. Sender-only, once per order. */
        @Headers("Content-Type: application/json")
        @POST("rest/v1/rpc/pay_with_wallet")
        Call<PaymentDto> payWithWallet(@Body OrderIdRequest body);

        /**
         * Records cash handed over at the door. Courier-only, and only once the
         * order is actually delivered — there is nothing to attest to before that.
         */
        @Headers("Content-Type: application/json")
        @POST("rest/v1/rpc/record_cash_payment")
        Call<PaymentDto> recordCash(@Body OrderIdRequest body);

        @GET("rest/v1/payments")
        Call<List<PaymentDto>> getForOrder(@Query("order_id") String orderFilter);
    }

    private final Api api = SupabaseClient.restClient().create(Api.class);

    public void payWithWallet(String orderId, PaymentCallback callback) {
        api.payWithWallet(new OrderIdRequest(orderId)).enqueue(single(callback));
    }

    public void recordCashPayment(String orderId, PaymentCallback callback) {
        api.recordCash(new OrderIdRequest(orderId)).enqueue(single(callback));
    }

    /**
     * Whether this order has been paid.
     *
     * <p>An empty result genuinely means unpaid: payments_select already narrows rows
     * to the payer, the payee or an admin, and both parties to an order are one of
     * those — so nobody who can see the order is shown a false negative.
     */
    public void getForOrder(String orderId, LookupCallback callback) {
        api.getForOrder("eq." + orderId).enqueue(new Callback<List<PaymentDto>>() {
            @Override
            public void onResponse(Call<List<PaymentDto>> call, Response<List<PaymentDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(response.body().isEmpty() ? null : response.body().get(0));
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<List<PaymentDto>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    private Callback<PaymentDto> single(PaymentCallback callback) {
        return new Callback<PaymentDto>() {
            @Override
            public void onResponse(Call<PaymentDto> call, Response<PaymentDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(readable(errorMessage(response)));
                }
            }

            @Override
            public void onFailure(Call<PaymentDto> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        };
    }

    /**
     * The functions raise French messages meant for the user; PostgREST wraps them in
     * JSON. Pulling the message out avoids showing somebody a raw error envelope.
     */
    private String readable(String body) {
        if (body == null) {
            return "Paiement impossible";
        }
        int i = body.indexOf("\"message\":\"");
        if (i < 0) {
            return body;
        }
        int start = i + 11;
        int end = body.indexOf('"', start);
        return end > start ? body.substring(start, end) : body;
    }

    private String errorMessage(Response<?> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : "Request failed";
        } catch (Exception e) {
            return "Request failed (" + response.code() + ")";
        }
    }
}
