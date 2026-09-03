package com.example.wassilapp.geo;

import android.content.Context;

import com.example.wassilapp.BuildConfig;
import com.example.wassilapp.R;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit client for api.mapbox.com.
 *
 * <p>Deliberately separate from SupabaseClient. That one attaches an apikey header
 * and the user's Supabase JWT to every request through an interceptor; reusing it
 * would send the user's session token to a third party on every distance lookup.
 * A different host needs a different client.
 */
public final class MapboxClient {

    private static Retrofit retrofit;
    private static String accessToken;

    private MapboxClient() {
    }

    /** Called once from WassilApplication, so screens do not each need a Context. */
    public static void init(Context context) {
        accessToken = context.getString(R.string.mapbox_access_token);
    }

    public static String token() {
        return accessToken;
    }

    public static MapboxGeoApi api() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BASIC
                    : HttpLoggingInterceptor.Level.NONE);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    // Short timeouts: this sits in front of a user waiting on a price
                    // estimate, and the haversine fallback is better than a long stall.
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.mapbox.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(MapboxGeoApi.class);
    }
}
