package com.example.wassilapp.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.wassilapp.R;
import com.example.wassilapp.activities.ChatActivity;
import com.example.wassilapp.activities.OrderTrackingActivity;

/**
 * Builds and posts the app's user-facing notifications.
 *
 * <p>Deliberately independent of how the event arrived. Whether a new message is
 * discovered by FCM waking the process, by a background poll, or by a screen that is
 * already open, the notification itself is identical — so the delivery mechanism can
 * change later without touching any of this.
 *
 * <p>Channels are separated because Android lets the user silence each one
 * individually. Someone who wants to mute chatter while a parcel is in transit should
 * not thereby mute "votre colis est arrivé".
 */
public final class WassilNotifications {

    public static final String CHANNEL_CHAT = "wassil_chat";
    public static final String CHANNEL_ORDERS = "wassil_orders";

    /**
     * Ids are derived from the order so a second message about the same delivery
     * replaces the first rather than stacking. A fixed id would collapse unrelated
     * orders together; a random one would leave a growing pile in the shade.
     */
    private static int idFor(String prefix, String orderId) {
        return (prefix + orderId).hashCode();
    }

    private WassilNotifications() {
    }

    /** Safe to call repeatedly; creating an existing channel is a no-op. */
    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // channels did not exist before Oreo
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel chat = new NotificationChannel(
                CHANNEL_CHAT, "Messages", NotificationManager.IMPORTANCE_HIGH);
        chat.setDescription("Messages entre le client et le livreur");

        NotificationChannel orders = new NotificationChannel(
                CHANNEL_ORDERS, "Livraisons", NotificationManager.IMPORTANCE_DEFAULT);
        orders.setDescription("Offres reçues et avancement de vos livraisons");

        manager.createNotificationChannel(chat);
        manager.createNotificationChannel(orders);
    }

    /** A new chat message on an order the user is party to. */
    public static void chatMessage(Context context, String orderId, String senderName,
                                   String body, String counterpartyPhone) {
        Intent open = new Intent(context, ChatActivity.class);
        open.putExtra(ChatActivity.EXTRA_ORDER_ID, orderId);
        open.putExtra(ChatActivity.EXTRA_NAME, senderName);
        open.putExtra(ChatActivity.EXTRA_PHONE, counterpartyPhone);
        open.putExtra(ChatActivity.EXTRA_CHAT_OPEN, true);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        post(context, CHANNEL_CHAT, idFor("chat", orderId),
                senderName != null ? senderName : "Nouveau message",
                body,
                open,
                NotificationCompat.PRIORITY_HIGH);
    }

    /** A delivery reached a new stage. */
    public static void orderStatus(Context context, String orderId, String title,
                                   String text, String userType) {
        Intent open = new Intent(context, OrderTrackingActivity.class);
        open.putExtra("order_id", orderId);
        open.putExtra("user_type", userType);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        post(context, CHANNEL_ORDERS, idFor("order", orderId), title, text, open,
                NotificationCompat.PRIORITY_DEFAULT);
    }

    private static void post(Context context, String channel, int id, String title,
                             String text, Intent openIntent, int priority) {
        // FLAG_IMMUTABLE is mandatory from Android 12; without it PendingIntent
        // construction throws outright rather than degrading.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(context, id, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_package)
                .setContentTitle(title)
                .setContentText(text)
                // Long messages are cut off in the collapsed view; BigTextStyle lets
                // the user expand rather than open the app just to read one line.
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pending);

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS denied on Android 13+. Nothing to do: the in-app
            // screens still show everything, and nagging here would be pointless.
        }
    }
}
