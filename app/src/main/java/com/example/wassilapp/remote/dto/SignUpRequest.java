package com.example.wassilapp.remote.dto;

/** Body for POST auth/v1/signup. */
public class SignUpRequest {
    public String email;
    public String password;

    public SignUpRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
