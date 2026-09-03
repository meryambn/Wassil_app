package com.example.wassilapp;

import android.app.Application;

import com.example.wassilapp.geo.MapboxClient;
import com.example.wassilapp.notifications.WassilNotifications;
import com.example.wassilapp.remote.SupabaseClient;

/**
 * Restores the saved Supabase session before any screen runs.
 *
 * <p>The access token lives in a static on {@link SupabaseClient}, which is empty
 * after a cold start. Without this, every REST call went out authenticated as the
 * anon key instead of the signed-in user, so row level security hid the user's own
 * rows and queries came back empty rather than failing loudly.
 */
public class WassilApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient.restoreSession(this);
        // Caches the public Mapbox token once, so screens that need distance or
        // a place name do not each have to thread a Context into the network layer.
        MapboxClient.init(this);
        // Channels must exist before anything tries to post to them, and creating
        // them is idempotent, so process start is the natural place.
        WassilNotifications.ensureChannels(this);
    }
}
