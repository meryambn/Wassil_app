package com.example.wassilapp.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.wassilapp.BuildConfig;
import com.example.wassilapp.utils.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Communicates with the Supabase Edge Function 'ai-assistant'.
 * Used strictly for natural conversational enrichment and FAQs.
 * Fallback to local rule-based engine is guaranteed on any failure.
 */
public class OnlineLLMService {

    public interface LlmCallback {
        void onSuccess(String reply);

        void onFallback();
    }

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public static void queryEdgeFunction(Context context, String userMessage, String role, LlmCallback callback) {
        if (BuildConfig.SUPABASE_URL.isEmpty()) {
            callback.onFallback();
            return;
        }

        String functionUrl = BuildConfig.SUPABASE_URL + "/functions/v1/ai-assistant";

        SessionManager session = new SessionManager(context);
        String token = session.getAccessToken();
        if (token == null || token.isEmpty()) {
            token = BuildConfig.SUPABASE_ANON_KEY;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("message", userMessage);
        payload.addProperty("role", role != null ? role : "sender");
        payload.addProperty("user_name", session.getUserName() != null ? session.getUserName() : "Utilisateur");

        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);

        Request request = new Request.Builder()
                .url(functionUrl)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .post(body)
                .build();

        Handler mainHandler = new Handler(Looper.getMainLooper());

        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(callback::onFallback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String bodyString = response.body().string();
                        JsonObject json = JsonParser.parseString(bodyString).getAsJsonObject();
                        if (json.has("reply") && !json.get("reply").isJsonNull()) {
                            String reply = json.get("reply").getAsString();
                            mainHandler.post(() -> callback.onSuccess(reply));
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                mainHandler.post(callback::onFallback);
            }
        });
    }
}
