package com.example.wassilapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Caches registration form fields keyed by email until the account's first
 * successful login. Needed because when Supabase requires email confirmation,
 * signup returns no session — so the profiles row (which requires an
 * authenticated request under RLS) can't be created until the user actually
 * logs in for the first time.
 */
public class PendingProfileStore {
    private static final String PREF_NAME = "PendingProfiles";

    public static void save(Context ctx, String email, String fullName, String phone,
                             String role, String wilaya, String commune, String vehicleType) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("full_name", fullName);
            obj.put("phone", phone);
            obj.put("role", role);
            obj.put("wilaya", wilaya);
            obj.put("commune", commune);
            obj.put("vehicle_type", vehicleType == null ? "" : vehicleType);
            prefs(ctx).edit().putString(email, obj.toString()).apply();
        } catch (JSONException e) {
            // Best-effort cache only; the Supabase auth account itself already exists.
        }
    }

    public static JSONObject consume(Context ctx, String email) {
        SharedPreferences prefs = prefs(ctx);
        String raw = prefs.getString(email, null);
        if (raw == null) {
            return null;
        }
        prefs.edit().remove(email).apply();
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
