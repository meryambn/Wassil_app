package com.example.wassilapp.remote;

import com.example.wassilapp.remote.dto.AuthSession;
import com.example.wassilapp.remote.dto.RefreshRequest;
import com.example.wassilapp.remote.dto.SignInRequest;
import com.example.wassilapp.remote.dto.SignUpRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Thin wrapper over {@link AuthApi} for Activities to call without touching Retrofit directly.
 * Callbacks land on the main thread (Retrofit's default Android behavior).
 */
public class AuthRepository {

    public interface AuthCallback {
        void onSuccess(AuthSession session);

        void onError(String message);
    }

    private final AuthApi api = SupabaseClient.authApi();

    public void signUp(String email, String password, AuthCallback callback) {
        api.signUp(new SignUpRequest(email, password)).enqueue(wrap(callback));
    }

    public void signIn(String email, String password, AuthCallback callback) {
        api.signIn("password", new SignInRequest(email, password)).enqueue(wrap(callback));
    }

    public void refresh(String refreshToken, AuthCallback callback) {
        api.refresh("refresh_token", new RefreshRequest(refreshToken)).enqueue(wrap(callback));
    }

    private Callback<AuthSession> wrap(AuthCallback callback) {
        return new Callback<AuthSession>() {
            @Override
            public void onResponse(Call<AuthSession> call, Response<AuthSession> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Keep the refresh token too, so an expired session can renew itself.
                    SupabaseClient.setSession(response.body().access_token,
                            response.body().refresh_token);
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(errorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<AuthSession> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        };
    }

    private String errorMessage(Response<AuthSession> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : "Request failed";
        } catch (Exception e) {
            return "Request failed (" + response.code() + ")";
        }
    }
}
