package com.example.wassilapp.notifications;

import com.example.wassilapp.remote.SupabaseClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Tells the backend which device this user is currently reachable on.
 *
 * <p>Registration goes through the register_device_token function rather than a
 * direct insert. The reason is the case where a phone changes hands: FCM reissues the
 * same registration token to whoever signs in next, and the row has to move to them.
 * A plain upsert cannot do that — an UPDATE policy tests the row as it currently
 * stands, so the previous owner's row blocks the new owner, and the old account
 * quietly keeps receiving deliveries on a phone it no longer has. The function runs
 * as the table owner and reassigns the token deliberately.
 */
public class DeviceTokenRepository {

    /** Body of the register_device_token RPC. */
    public static class RegisterRequest {
        public String p_token;
        public String p_platform;

        RegisterRequest(String token) {
            this.p_token = token;
            this.p_platform = "android";
        }
    }

    private interface Api {
        @Headers("Content-Type: application/json")
        @POST("rest/v1/rpc/register_device_token")
        Call<Void> register(@Body RegisterRequest body);

        /**
         * Retires one token. Restricted by RLS to the caller's own rows, so a user
         * cannot unregister somebody else's device even knowing its token.
         */
        @DELETE("rest/v1/device_tokens")
        Call<Void> unregister(@Query("token") String tokenFilter);
    }

    public interface Callback0 {
        void onDone(boolean success);
    }

    private final Api api = SupabaseClient.restClient().create(Api.class);

    /**
     * Called after sign-in and whenever FCM rotates the token.
     *
     * <p>Failure is not surfaced to the user: they did not ask for this, and the app
     * works without it — they simply will not be notified while it is closed.
     */
    public void register(String fcmToken, Callback0 callback) {
        if (fcmToken == null || fcmToken.length() < 20) {
            if (callback != null) callback.onDone(false);
            return;
        }
        api.register(new RegisterRequest(fcmToken)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (callback != null) callback.onDone(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (callback != null) callback.onDone(false);
            }
        });
    }

    /**
     * Called on logout, before the session is cleared.
     *
     * <p>Order matters: this needs the signed-in user's JWT to pass RLS, so clearing
     * the session first would leave the row behind and send the next person to use
     * this phone somebody else's messages.
     */
    public void unregister(String fcmToken, Callback0 callback) {
        if (fcmToken == null) {
            if (callback != null) callback.onDone(false);
            return;
        }
        api.unregister("eq." + fcmToken).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (callback != null) callback.onDone(response.isSuccessful());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (callback != null) callback.onDone(false);
            }
        });
    }
}
