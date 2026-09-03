package com.example.wassilapp.remote.dto;

/** Body for POST auth/v1/token?grant_type=refresh_token. */
public class RefreshRequest {
    public String refresh_token;

    public RefreshRequest(String refreshToken) {
        this.refresh_token = refreshToken;
    }
}
