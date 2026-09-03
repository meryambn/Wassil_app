package com.example.wassilapp.remote.dto;

/**
 * One row of public.withdrawals.
 *
 * <p>A DTO ("data transfer object") is a plain class whose field names match the
 * JSON the server sends, so Gson can fill it in automatically. Nothing here has
 * behaviour — it is only a shape for data crossing the network.
 */
public class WithdrawalDto {
    public String id;
    public String courier_id;
    public double amount;
    public String status;        // pending | approved | rejected
    public String requested_at;
    public String resolved_at;
    public String resolved_by;
    public String note;              // rejection reason, set by resolve_withdrawal
    public String payout_reference;  // real transfer id, set by mark_withdrawal_paid

    /**
     * The requesting courier, pulled in the same round trip via PostgREST resource
     * embedding (select=*,courier:profiles!courier_id(...)). Reuses OrderDto's
     * ProfileRef so there is one shape for "a profile summary" across the app.
     * Null when the embed was not requested.
     */
    public OrderDto.ProfileRef courier;
}
