package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.CancelWithdrawalRequest;
import com.example.wassilapp.remote.dto.MarkPaidRequest;
import com.example.wassilapp.remote.dto.RequestWithdrawalRequest;
import com.example.wassilapp.remote.dto.ResolveWithdrawalRequest;
import com.example.wassilapp.remote.dto.WithdrawalDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The screen's only door to withdrawal data.
 *
 * <p>A "repository" hides HOW data is obtained. EarningsActivity says "please
 * withdraw 500" and gets back success or an error; it never knows about HTTP,
 * Supabase or SQL. That separation is why the balance can move from the client to
 * the server without rewriting the screen's logic.
 *
 * <p>Network calls cannot block the screen (Android kills apps that freeze), so
 * results arrive through a callback: an object whose methods are invoked later,
 * once the answer comes back.
 */
public class WithdrawalRepository {

    public interface WithdrawalCallback {
        void onRequested(WithdrawalDto withdrawal);

        /** Server refused — e.g. "Solde insuffisant" or "Montant invalide". */
        void onRejected(String reason);

        /** Could not reach the server at all; nothing was withdrawn. */
        void onError(String message);
    }

    public interface HistoryCallback {
        void onSuccess(List<WithdrawalDto> withdrawals);

        void onError(String message);
    }

    /** Result of an admin action (approve / reject / mark paid). */
    public interface ActionCallback {
        void onSuccess(WithdrawalDto updated);

        void onError(String message);
    }

    /** Pulls the requesting courier's profile in the same round trip. */
    private static final String SELECT_WITH_COURIER =
            "*,courier:profiles!courier_id(id,full_name,phone,vehicle_type,rating)";

    /**
     * status.asc puts pending first, then approved, paid, rejected — PostgREST sorts
     * an enum by its DECLARED order, not alphabetically, and the withdrawal_status
     * enum was declared in exactly that lifecycle order. Verified against the live
     * API. Newest first within each status.
     */
    private static final String ORDER_ACTIONABLE_FIRST = "status.asc,requested_at.desc";

    private final WithdrawalsApi api = SupabaseClient.restClient().create(WithdrawalsApi.class);

    public void requestWithdrawal(double amount, WithdrawalCallback callback) {
        api.requestWithdrawal(new RequestWithdrawalRequest(amount))
                .enqueue(new Callback<WithdrawalDto>() {
                    @Override
                    public void onResponse(Call<WithdrawalDto> call, Response<WithdrawalDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onRequested(response.body());
                            return;
                        }
                        // A raise exception inside the SQL function comes back as a
                        // 4xx carrying the French message we wrote there.
                        String body = errorBody(response);
                        String reason = extractMessage(body);
                        if (response.code() >= 400 && response.code() < 500) {
                            callback.onRejected(reason);
                        } else {
                            callback.onError(reason);
                        }
                    }

                    @Override
                    public void onFailure(Call<WithdrawalDto> call, Throwable t) {
                        // Never assume the withdrawal failed on the server here: the
                        // request may have succeeded and only the reply was lost. The
                        // caller re-reads the authoritative balance instead of guessing.
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    /** All withdrawals, actionable ones first. Scope is decided by RLS, not by us. */
    public void getAllWithdrawals(HistoryCallback callback) {
        api.getAll(ORDER_ACTIONABLE_FIRST, SELECT_WITH_COURIER).enqueue(listHandler(callback));
    }

    /**
     * Approve or reject. The decision is made by the database function, which checks
     * is_admin() itself — hiding the buttons in the app is convenience, not security.
     */
    public void resolve(String withdrawalId, boolean approve, String note, ActionCallback callback) {
        api.resolveWithdrawal(new ResolveWithdrawalRequest(withdrawalId, approve, note))
                .enqueue(actionHandler(callback));
    }

    /** Record the real transfer. The server rejects a blank reference and non-'approved' rows. */
    public void markPaid(String withdrawalId, String payoutReference, ActionCallback callback) {
        api.markPaid(new MarkPaidRequest(withdrawalId, payoutReference))
                .enqueue(actionHandler(callback));
    }

    /**
     * Abandon an approved payout; the server refunds the reserved amount.
     * Use only when the payout is being given up on — a failed attempt alone needs
     * no call, since the row stays 'approved' and markPaid can be retried.
     */
    public void cancel(String withdrawalId, String reason, ActionCallback callback) {
        api.cancelWithdrawal(new CancelWithdrawalRequest(withdrawalId, reason))
                .enqueue(actionHandler(callback));
    }

    private Callback<WithdrawalDto> actionHandler(ActionCallback callback) {
        return new Callback<WithdrawalDto>() {
            @Override
            public void onResponse(Call<WithdrawalDto> call, Response<WithdrawalDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    // The French text raised by the SQL function arrives here, so the
                    // user sees the server's own reason rather than a generic error.
                    callback.onError(extractMessage(errorBody(response)));
                }
            }

            @Override
            public void onFailure(Call<WithdrawalDto> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Erreur réseau");
            }
        };
    }

    private Callback<List<WithdrawalDto>> listHandler(HistoryCallback callback) {
        return new Callback<List<WithdrawalDto>>() {
            @Override
            public void onResponse(Call<List<WithdrawalDto>> call, Response<List<WithdrawalDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(extractMessage(errorBody(response)));
                }
            }

            @Override
            public void onFailure(Call<List<WithdrawalDto>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Erreur réseau");
            }
        };
    }

    public void getMyWithdrawals(String courierUid, HistoryCallback callback) {
        api.getMine("eq." + courierUid, "requested_at.desc", "*")
                .enqueue(new Callback<List<WithdrawalDto>>() {
                    @Override
                    public void onResponse(Call<List<WithdrawalDto>> call, Response<List<WithdrawalDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError(extractMessage(errorBody(response)));
                        }
                    }

                    @Override
                    public void onFailure(Call<List<WithdrawalDto>> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
                    }
                });
    }

    private String errorBody(Response<?> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Pulls the human-readable text out of PostgREST's {"message":"..."} payload. */
    private String extractMessage(String body) {
        if (body == null || body.isEmpty()) {
            return "Erreur inconnue";
        }
        int i = body.indexOf("\"message\":\"");
        if (i < 0) {
            return body;
        }
        int start = i + "\"message\":\"".length();
        int end = body.indexOf('"', start);
        return end > start ? body.substring(start, end) : body;
    }
}
