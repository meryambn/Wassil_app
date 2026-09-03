package com.example.wassilapp.remote.dto;

/**
 * Body for the request_withdrawal database function.
 *
 * <p>The field name must match the function's parameter name exactly — PostgREST
 * maps JSON keys onto named SQL arguments, so {@code p_amount} here lines up with
 * {@code request_withdrawal(p_amount numeric)} in the database.
 *
 * <p>Note there is no courier id: the server takes the identity from the signed-in
 * user's token (auth.uid()), so a client cannot request a withdrawal for someone else.
 */
public class RequestWithdrawalRequest {
    public double p_amount;

    public RequestWithdrawalRequest(double amount) {
        this.p_amount = amount;
    }
}
