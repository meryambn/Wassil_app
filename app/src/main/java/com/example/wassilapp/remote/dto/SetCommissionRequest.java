package com.example.wassilapp.remote.dto;

/**
 * Body for the set_platform_commission RPC.
 */
public class SetCommissionRequest {
    public double p_percentage;

    public SetCommissionRequest(double p_percentage) {
        this.p_percentage = p_percentage;
    }
}
