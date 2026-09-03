package com.example.wassilapp.remote.dto;

/**
 * Body for the resolve_withdrawal database function (approve or reject).
 *
 * <p>Field names mirror the SQL parameter names exactly — PostgREST matches JSON
 * keys to named arguments, so renaming a field here silently breaks the call.
 *
 * <p>No admin id is sent: the function reads the caller's identity from the JWT via
 * auth.uid() and checks is_admin() itself. The client cannot claim to be someone else.
 */
public class ResolveWithdrawalRequest {
    public String p_withdrawal_id;
    public boolean p_approve;
    public String p_note;

    public ResolveWithdrawalRequest(String withdrawalId, boolean approve, String note) {
        this.p_withdrawal_id = withdrawalId;
        this.p_approve = approve;
        this.p_note = (note == null || note.trim().isEmpty()) ? null : note.trim();
    }
}
