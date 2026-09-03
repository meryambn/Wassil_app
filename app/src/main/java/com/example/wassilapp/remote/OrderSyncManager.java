package com.example.wassilapp.remote;

import android.content.Context;

import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.remote.dto.Profile;

import java.util.List;

/**
 * Pulls authoritative order state from Supabase into the local SQLite cache.
 *
 * <p>Why this exists as its own class rather than living on {@link OrderRepository}:
 * the repository is deliberately Context-free (pure remote access), while writing to
 * SQLite needs a Context for {@link DatabaseHelper}. Keeping them separate means the
 * repository's constructor and all its call sites stay untouched.
 *
 * <p>Screens do not change how they read data — they keep their synchronous
 * {@code db.getX()} calls and simply re-read once {@link SyncCallback} fires.
 */
public class OrderSyncManager {

    public interface SyncCallback {
        void onSynced(int orderCount);

        void onError(String message);
    }

    private final DatabaseHelper db;
    private final OrderRepository orderRepository = new OrderRepository();

    public OrderSyncManager(Context context) {
        // Application context: this outlives any single Activity's callback.
        this.db = new DatabaseHelper(context.getApplicationContext());
    }

    /**
     * Refreshes every order I'm party to (as sender or as assigned courier).
     *
     * @param myUid         my Supabase profiles.id; null for local-only accounts
     * @param myLocalUserId my SQLite users.id, used so existing screens' int-based
     *                      joins keep resolving
     * @param myName        my display name, for rows where I'm the sender
     */
    public void syncMyOrders(String myUid, int myLocalUserId, String myName, SyncCallback callback) {
        if (myUid == null) {
            callback.onError("Compte local uniquement — aucune synchronisation possible");
            return;
        }

        orderRepository.getMyOrders(myUid, new OrderRepository.OrderListCallback() {
            @Override
            public void onSuccess(List<OrderDto> orders) {
                for (OrderDto dto : orders) {
                    applyToLocalCache(dto, myUid, myLocalUserId, myName);
                }
                callback.onSynced(orders.size());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Every order on the platform. Returns only the caller's own orders unless admin. */
    public void syncAllOrders(String myUid, int myLocalUserId, String myName, SyncCallback callback) {
        if (myUid == null) {
            callback.onError("Compte local uniquement — aucune synchronisation possible");
            return;
        }

        orderRepository.getAllOrders(new OrderRepository.OrderListCallback() {
            @Override
            public void onSuccess(List<OrderDto> orders) {
                for (OrderDto dto : orders) {
                    applyToLocalCache(dto, myUid, myLocalUserId, myName);
                }
                callback.onSynced(orders.size());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Refreshes my own balance and rating from Supabase.
     *
     * <p>Needed because the courier's fee is now credited by a database trigger when
     * an order reaches 'livre', so the authoritative balance changes without this
     * device doing anything.
     */
    public void syncMyProfile(String myUid, int myLocalUserId, SyncCallback callback) {
        if (myUid == null) {
            callback.onError("Compte local uniquement");
            return;
        }
        new ProfileRepository().getById(myUid, new ProfileRepository.ProfileLookupCallback() {
            @Override
            public void onFound(Profile profile) {
                db.updateUserBalance(myLocalUserId, profile.balance);
                db.updateDeliveryRating(myLocalUserId, profile.rating);
                callback.onSynced(1);
            }

            @Override
            public void onNotFound() {
                callback.onError("Profil introuvable");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void applyToLocalCache(OrderDto dto, String myUid, int myLocalUserId, String myName) {
        int deliveryLocalId = 0;
        String deliveryName = "—";
        if (dto.delivery != null && dto.delivery.id != null) {
            deliveryLocalId = resolveLocalUserId(dto.delivery, "delivery", myUid, myLocalUserId);
            deliveryName = dto.delivery.full_name;
        }

        int senderLocalId = 0;
        String senderName = "";
        if (dto.sender != null && dto.sender.id != null) {
            senderLocalId = resolveLocalUserId(dto.sender, "sender", myUid, myLocalUserId);
            senderName = dto.sender.full_name;
        } else if (myUid.equals(dto.sender_id)) {
            // Embed was denied or omitted, but it's my own order.
            senderLocalId = myLocalUserId;
            senderName = myName;
        }

        Order order = OrderMapper.toOrder(dto, senderLocalId, senderName,
                deliveryLocalId, deliveryName);
        db.upsertOrderFromCloud(order, dto.delivery_id);
    }

    /**
     * Maps an embedded cloud profile to a local users.id, creating a shadow row for
     * counterparties who have never signed in on this device. Existing screens still
     * join on the integer id, so this mapping is what lets them stay unchanged.
     */
    private int resolveLocalUserId(OrderDto.ProfileRef ref, String role,
                                    String myUid, int myLocalUserId) {
        if (ref.id.equals(myUid)) {
            // Never shadow myself — my own row already exists and owns my real role.
            return myLocalUserId;
        }
        return db.upsertUserFromCloud(ref.id, ref.full_name, ref.phone,
                role, ref.vehicle_type, ref.rating);
    }
}
