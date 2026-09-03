package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.AuthSession;
import com.example.wassilapp.remote.dto.RefreshRequest;
import com.example.wassilapp.remote.dto.SignInRequest;
import com.example.wassilapp.remote.dto.SignUpRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** GoTrue (Supabase Auth) endpoints. */
public interface AuthApi {

    @POST("auth/v1/signup")
    Call<AuthSession> signUp(@Body SignUpRequest body);

    @POST("auth/v1/token")
    Call<AuthSession> signIn(@Query("grant_type") String grantType, @Body SignInRequest body);

    @POST("auth/v1/token")
    Call<AuthSession> refresh(@Query("grant_type") String grantType, @Body RefreshRequest body);
}
