package com.example.wassilapp.remote;

import android.content.Context;

import com.example.wassilapp.BuildConfig;
import com.example.wassilapp.remote.dto.AuthSession;
import com.example.wassilapp.remote.dto.RefreshRequest;
import com.example.wassilapp.utils.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Builds Retrofit clients for Supabase's REST APIs (GoTrue auth, PostgREST). */
public final class SupabaseClient {

    private static Retrofit authRetrofit;
    private static Retrofit restRetrofit;
    private static volatile String accessToken;
    private static volatile String refreshToken;
    private static Context appContext;

    private SupabaseClient() {
    }

    public static void setAccessToken(String token) {
        accessToken = token;
    }

    public static void setSession(String access, String refresh) {
        accessToken = access;
        refreshToken = refresh;
    }

    /**
     * Loads the persisted session into memory at process start.
     *
     * <p>Without this the tokens are only ever set by a live login, so after the app
     * is killed and reopened every request authenticates as the anon key. That does
     * not error — RLS simply hides the user's rows — so it surfaces as unexplained
     * empty lists rather than an auth failure.
     */
    public static void restoreSession(Context context) {
        appContext = context.getApplicationContext();
        SessionManager session = new SessionManager(appContext);
        accessToken = session.getAccessToken();
        refreshToken = session.getRefreshToken();
    }

    private static void requireConfig() {
        if (BuildConfig.SUPABASE_URL.isEmpty() || BuildConfig.SUPABASE_ANON_KEY.isEmpty()) {
            throw new IllegalStateException(
                    "SUPABASE_URL / SUPABASE_ANON_KEY are not set. Add them to local.properties " +
                            "(see supabase/schema.sql header) then rebuild.");
        }
    }

    /** Client for auth.v1 endpoints (signup/login) — only needs the anon apikey header. */
    public static AuthApi authApi() {
        requireConfig();
        if (authRetrofit == null) {
            authRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl())
                    .client(httpClient(false))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return authRetrofit.create(AuthApi.class);
    }

    /** Client for rest/v1 (PostgREST) endpoints — sends the signed-in user's JWT when present. */
    public static Retrofit restClient() {
        requireConfig();
        if (restRetrofit == null) {
            restRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl())
                    .client(httpClient(true))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return restRetrofit;
    }

    public static ProfilesApi profilesApi() {
        return restClient().create(ProfilesApi.class);
    }

    private static String baseUrl() {
        String url = BuildConfig.SUPABASE_URL;
        return url.endsWith("/") ? url : url + "/";
    }

    private static OkHttpClient httpClient(boolean forwardUserToken) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        Interceptor headers = chain -> {
            Request original = chain.request();
            Request.Builder builder = original.newBuilder()
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY);

            // Only supply a default content type when the request has not declared
            // one. This used to be added unconditionally, which was invisible for
            // JSON calls (Gson sets the same value anyway) but broke every binary
            // upload: a JPEG sent to Storage went out labelled application/json, and
            // the bucket rejected it as a disallowed MIME type with a bare 400.
            // addHeader appends rather than replaces, so the wrong value could not
            // even be overridden further down the chain.
            boolean bodyDeclaresType = original.body() != null
                    && original.body().contentType() != null;
            if (original.header("Content-Type") == null && !bodyDeclaresType) {
                builder.addHeader("Content-Type", "application/json");
            }

            String token = forwardUserToken ? accessToken : null;
            builder.addHeader("Authorization", "Bearer " + (token != null ? token : BuildConfig.SUPABASE_ANON_KEY));
            return chain.proceed(builder.build());
        };

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(headers)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS);

        if (forwardUserToken) {
            builder.authenticator(refreshAuthenticator());
        }
        return builder.build();
    }

    /**
     * Swaps an expired access token for a fresh one on a 401 and retries once.
     * Supabase access tokens last about an hour, so long sessions would otherwise
     * start silently falling back to anon-level access.
     */
    private static Authenticator refreshAuthenticator() {
        return new Authenticator() {
            @Override
            public Request authenticate(Route route, Response response) throws IOException {
                // responseCount > 1 means we already retried with a refreshed token.
                if (response.priorResponse() != null || refreshToken == null) {
                    return null;
                }

                synchronized (SupabaseClient.class) {
                    String tokenWhenFailed = response.request().header("Authorization");
                    String current = "Bearer " + accessToken;
                    if (accessToken != null && !current.equals(tokenWhenFailed)) {
                        // Another thread already refreshed; just retry with the new one.
                        return response.request().newBuilder()
                                .header("Authorization", current)
                                .build();
                    }

                    retrofit2.Response<AuthSession> refreshed = authApi()
                            .refresh("refresh_token", new RefreshRequest(refreshToken))
                            .execute();
                    if (!refreshed.isSuccessful() || refreshed.body() == null) {
                        return null; // refresh token itself is dead; user must log in again
                    }

                    AuthSession s = refreshed.body();
                    setSession(s.access_token, s.refresh_token);
                    if (appContext != null) {
                        SessionManager session = new SessionManager(appContext);
                        // Keep the existing uid when the refresh payload omits the user,
                        // otherwise sync loses the identity it maps rows by.
                        String uid = (s.user != null && s.user.id != null)
                                ? s.user.id : session.getSupabaseUid();
                        session.saveSupabaseSession(uid, session.getEmail(),
                                s.access_token, s.refresh_token);
                    }
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + s.access_token)
                            .build();
                }
            }
        };
    }
}
