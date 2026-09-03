package com.example.wassilapp.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.wassilapp.remote.SupabaseClient;
import com.example.wassilapp.utils.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives pushes from FCM.
 *
 * <p>Android starts this service on the app's behalf even when no Activity is
 * running, which is the whole reason for using FCM rather than polling: a courier
 * with the app closed and the phone in their pocket still gets told about a new job.
 */
public class WassilMessagingService extends FirebaseMessagingService {

    private static final String TAG = "WassilFCM";

    private final DeviceTokenRepository tokens = new DeviceTokenRepository();

    /**
     * FCM issues a new registration token on install, and rotates it afterwards
     * (restore to a new device, app data cleared, periodic refresh). A token that is
     * only ever read once at login goes stale silently — the server keeps sending to
     * an address nobody is listening on — so it is re-registered whenever it changes.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        // Cold start: the process may have been created purely to deliver this
        // callback, so the auth session has to be restored before the write can pass
        // RLS. Without this the request goes out as the anon key and is rejected.
        SupabaseClient.restoreSession(getApplicationContext());

        SessionManager session = new SessionManager(getApplicationContext());
        if (session.getSupabaseUid() == null) {
            // Nobody is signed in on this device yet. Registration happens at login.
            return;
        }
        tokens.register(token, success ->
                Log.d(TAG, "token registration " + (success ? "ok" : "failed")));
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> data = message.getData();
        String orderId = data.get("order_id");
        String kind = data.get("kind");

        String title = null;
        String body = null;
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (title == null) title = data.get("title");
        if (body == null) body = data.get("body");
        if (title == null) {
            return; // nothing worth showing
        }

        // The notification is rebuilt locally rather than left to FCM's own display
        // path, so it lands on the right channel and its tap target is the correct
        // screen. FCM's automatic display only happens when the app is backgrounded
        // and would open the launcher activity with no idea which order it concerns.
        WassilNotifications.ensureChannels(getApplicationContext());
        if ("chat".equals(kind)) {
            WassilNotifications.chatMessage(getApplicationContext(), orderId, title, body, null);
        } else {
            SessionManager session = new SessionManager(getApplicationContext());
            String role = session.getUserRole();
            WassilNotifications.orderStatus(getApplicationContext(), orderId, title, body,
                    "delivery".equals(role) ? "delivery" : "sender");
        }
    }
}
