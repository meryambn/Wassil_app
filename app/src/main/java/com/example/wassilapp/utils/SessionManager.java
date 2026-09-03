package com.example.wassilapp.utils;



import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "DeliverySession";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    // Supabase identity — kept alongside the legacy SQLite fields above so
    // screens not yet migrated off DatabaseHelper keep working unchanged.
    private static final String KEY_SUPABASE_UID = "supabase_uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(int userId, String userName, String role) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, userName);
        editor.putString(KEY_USER_ROLE, role);
        editor.commit();
    }

    public void saveSupabaseSession(String supabaseUid, String email, String accessToken, String refreshToken) {
        editor.putString(KEY_SUPABASE_UID, supabaseUid);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_ACCESS_TOKEN, accessToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.commit();
    }

    public String getSupabaseUid() {
        return pref.getString(KEY_SUPABASE_UID, null);
    }

    public String getEmail() {
        return pref.getString(KEY_EMAIL, null);
    }

    public String getAccessToken() {
        return pref.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return pref.getString(KEY_REFRESH_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return pref.getInt(KEY_USER_ID, -1);
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, null);
    }

    public String getUserRole() {
        return pref.getString(KEY_USER_ROLE, null);
    }

    public void logout() {
        editor.clear();
        editor.commit();
    }
}
