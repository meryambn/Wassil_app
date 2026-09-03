package com.example.wassilapp.remote.dto;

/** GoTrue's session response shape for signup/login/refresh. */
public class AuthSession {
    public String access_token;
    public String token_type;
    public long expires_in;
    public String refresh_token;
    public SupabaseUser user;
}
