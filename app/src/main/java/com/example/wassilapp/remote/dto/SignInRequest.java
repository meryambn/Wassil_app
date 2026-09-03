package com.example.wassilapp.remote.dto;

/** Body for POST auth/v1/token?grant_type=password. */
public class SignInRequest {
    public String email;
    public String password;

    public SignInRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
