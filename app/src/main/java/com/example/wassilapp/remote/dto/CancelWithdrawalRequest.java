package com.example.wassilapp.remote.dto;

/**
 * Body for the cancel_withdrawal database function.
 *
 * <p>Used when an approved payout is abandoned (transfer impossible, wrong bank
 * details). The reserved money is returned to the courier by the server.
 *
 * <p>Not for a single failed attempt: failure changes nothing in the database, so
 * the admin can simply try mark_withdrawal_paid again.
 */
public class CancelWithdrawalRequest {
    public String p_withdrawal_id;
    public String p_reason;

    public CancelWithdrawalRequest(String withdrawalId, String reason) {
        this.p_withdrawal_id = withdrawalId;
        this.p_reason = (reason == null || reason.trim().isEmpty()) ? null : reason.trim();
    }
}
