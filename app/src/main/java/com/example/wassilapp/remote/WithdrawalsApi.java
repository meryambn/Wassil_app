package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.CancelWithdrawalRequest;
import com.example.wassilapp.remote.dto.MarkPaidRequest;
import com.example.wassilapp.remote.dto.RequestWithdrawalRequest;
import com.example.wassilapp.remote.dto.ResolveWithdrawalRequest;
import com.example.wassilapp.remote.dto.WithdrawalDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * HTTP calls for withdrawals, declared the Retrofit way: each method describes a
 * request, and Retrofit generates the code that actually performs it.
 */
public interface WithdrawalsApi {

    /**
     * Calls the request_withdrawal database function.
     *
     * <p>"rpc" = remote procedure call: instead of the client doing
     * read-balance / check / subtract / insert as separate requests (which could
     * interleave with another request and overdraw the wallet), it asks the server
     * to run all of it as one indivisible operation.
     */
    @POST("rest/v1/rpc/request_withdrawal")
    Call<WithdrawalDto> requestWithdrawal(@Body RequestWithdrawalRequest body);

    /** This courier's withdrawal history — the spec's "Retraits" list. */
    @GET("rest/v1/withdrawals")
    Call<List<WithdrawalDto>> getMine(@Query("courier_id") String courierIdFilter,
                                       @Query("order") String order,
                                       @Query("select") String select);

    /**
     * Every withdrawal on the platform, for the admin screen.
     *
     * <p>There is no admin filter in this request, and there does not need to be:
     * the withdrawals_select policy returns only the caller's own rows unless
     * is_admin() is true. A non-admin calling this simply sees their own history.
     * The server decides scope, not the client.
     */
    @GET("rest/v1/withdrawals")
    Call<List<WithdrawalDto>> getAll(@Query("order") String order,
                                      @Query("select") String select);

    /** Approve or reject. Rejecting refunds the reserved amount, server-side. */
    @POST("rest/v1/rpc/resolve_withdrawal")
    Call<WithdrawalDto> resolveWithdrawal(@Body ResolveWithdrawalRequest body);

    /** Record that the real transfer happened. Only valid on an 'approved' row. */
    @POST("rest/v1/rpc/mark_withdrawal_paid")
    Call<WithdrawalDto> markPaid(@Body MarkPaidRequest body);

    /**
     * Abandon an approved payout and refund the courier. Only valid on 'approved'.
     * A merely failed attempt needs no call at all — just retry markPaid later.
     */
    @POST("rest/v1/rpc/cancel_withdrawal")
    Call<WithdrawalDto> cancelWithdrawal(@Body CancelWithdrawalRequest body);
}
