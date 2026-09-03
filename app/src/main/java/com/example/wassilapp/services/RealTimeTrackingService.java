package com.example.wassilapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.wassilapp.remote.LocationRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Publishes the courier's position while a delivery is in progress.
 *
 * <p>A Service is a component with no user interface that keeps running when the
 * screen changes. That is essential here: a courier locks their phone or switches to
 * a navigation app while driving, and an Activity-bound location listener would die
 * at that moment. It runs in the FOREGROUND (with a visible notification) because
 * since Android 8 background location is throttled to a few updates per hour, and
 * Android 10+ requires a separate background-location permission. The notification
 * is also honest: the courier can see that they are being tracked.
 *
 * <p>Start it with the order id and the courier's Supabase id:
 * <pre>
 *   Intent i = new Intent(ctx, RealTimeTrackingService.class);
 *   i.putExtra(EXTRA_ORDER_ID, orderId);
 *   i.putExtra(EXTRA_COURIER_UID, session.getSupabaseUid());
 *   ContextCompat.startForegroundService(ctx, i);
 * </pre>
 */
public class RealTimeTrackingService extends Service {

    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_COURIER_UID = "courier_uid";

    private static final String CHANNEL_ID = "TrackingChannel";
    private static final int NOTIFICATION_ID = 1001;

    /**
     * Update strategy: at most one fix every 15 s, and only after moving 50 m.
     *
     * <p>The previous values (5 s / 2 s) were navigation-grade. For "where is my
     * parcel" that is wasted battery and wasted writes — a parcel that moved eight
     * metres is not news. The distance filter is applied by the OS, so a courier
     * stopped at a traffic light produces ZERO network calls.
     */
    private static final long UPDATE_INTERVAL_MS = 15_000L;
    private static final long FASTEST_INTERVAL_MS = 10_000L;
    private static final float MIN_DISTANCE_M = 50f;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private final LocationRepository locationRepository = new LocationRepository();

    private String orderId;
    private String courierUid;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasExtra(EXTRA_ORDER_ID)) {
                orderId = intent.getStringExtra(EXTRA_ORDER_ID);
            }
            if (intent.hasExtra(EXTRA_COURIER_UID)) {
                courierUid = intent.getStringExtra(EXTRA_COURIER_UID);
            }
        }

        // Without both ids there is nothing meaningful to publish, and the row would
        // be rejected by row level security anyway. Stop rather than burn GPS.
        if (orderId == null || courierUid == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        startLocationUpdates();

        // START_REDELIVER_INTENT: if Android kills us under memory pressure it
        // restarts us WITH the original extras, so we keep tracking the same order.
        // (START_STICKY would restart with a null intent and lose the ids.)
        return START_REDELIVER_INTENT;
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location == null || orderId == null || courierUid == null) {
                    return;
                }
                // Straight to Supabase. The old code wrote nowhere: it compared an
                // INTEGER courier id from local SQLite, which cannot match a Supabase
                // UUID, and its database write was commented out.
                locationRepository.publish(orderId, courierUid,
                        location.getLatitude(), location.getLongitude());
            }
        };

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } else {
            // Permission was revoked while running: nothing to do, so do not pretend
            // to track. The Activity is responsible for asking before starting us.
            stopSelf();
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Livraison en cours")
                .setContentText("Votre position est partagée avec l'expéditeur")
                .setSmallIcon(android.R.drawable.ic_menu_mapmode)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Suivi de livraison", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Partage de position pendant une livraison");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Releasing the listener matters: without this the GPS radio keeps running
        // after the delivery ends, draining the courier's battery all day.
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
