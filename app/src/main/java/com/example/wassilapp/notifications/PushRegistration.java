package com.example.wassilapp.notifications;

import android.content.Context;
import android.util.Log;

import com.example.wassilapp.utils.SessionManager;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Keeps the backend's idea of "where this user is reachable" in step with reality.
 *
 * <p>Two paths are needed and neither is redundant. {@link WassilMessagingService}
 * catches the token <i>changing</i>, but FCM only fires that when it actually rotates
 * — so a user signing in on a device whose token has been stable for months would
 * never register at all. This class covers the other direction: the token is
 * unchanged, but who is signed in has changed.
 */
public final class PushRegistration {

    private static final String TAG = "WassilFCM";

    /** Arbitrary; only has to be unique within the requesting Activity. */
    public static final int REQ_NOTIFICATION_PERMISSION = 5001;

    private PushRegistration() {
    }

    /**
     * Asks for POST_NOTIFICATIONS on Android 13+.
     *
     * <p>Declaring it in the manifest is not enough on 33 and above — it is a runtime
     * permission, and until it is granted every notify() call is dropped by the
     * system. That failure is completely silent from the app's side: FCM delivers,
     * the service runs, the notification is built, and nothing appears. Measured on
     * the emulator as {@code granted=false, importance=NONE} while pushes were
     * arriving successfully.
     */
    public static void ensurePermission(android.app.Activity activity) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return; // granted at install time before 13
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(activity,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        androidx.core.app.ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQ_NOTIFICATION_PERMISSION);
    }

    /** Call after a successful sign-in, and on launch when a session already exists. */
    public static void syncToken(Context context) {
        SessionManager session = new SessionManager(context);
        if (session.getSupabaseUid() == null) {
            return;
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token ->
                        new DeviceTokenRepository().register(token, ok ->
                                Log.d(TAG, "sync token " + (ok ? "ok" : "failed"))))
                .addOnFailureListener(e ->
                        // No Play Services, or Firebase misconfigured. The app is
                        // fully usable without push, so this stays a log line.
                        Log.w(TAG, "FCM token unavailable: " + e.getMessage()));
    }

    /**
     * Call on logout, <b>before</b> the session is cleared.
     *
     * <p>The delete is authorised by the signed-in user's JWT, so clearing the
     * session first would leave the row behind — and the next person to sign in on
     * this phone would keep receiving the previous user's deliveries until their own
     * registration overwrote it.
     */
    public static void retireToken(Context context, Runnable then) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token ->
                        new DeviceTokenRepository().unregister(token, ok -> {
                            Log.d(TAG, "retire token " + (ok ? "ok" : "failed"));
                            if (then != null) then.run();
                        }))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "FCM token unavailable on logout: " + e.getMessage());
                    if (then != null) then.run();
                });
    }
}
