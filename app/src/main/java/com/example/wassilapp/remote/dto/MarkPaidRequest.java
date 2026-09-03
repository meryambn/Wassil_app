package com.example.wassilapp.remote.dto;

/**
 * Body for the mark_withdrawal_paid database function.
 *
 * <p>The payout reference is the real-world transfer id (CCP / BaridiMob / bank
 * receipt). The database rejects a blank one, so this is not merely a UI hint.
 */
public class MarkPaidRequest {
    public String p_withdrawal_id;
    public String p_payout_reference;

    public MarkPaidRequest(String withdrawalId, String payoutReference) {
        this.p_withdrawal_id = withdrawalId;
        this.p_payout_reference = payoutReference;
    }
}
