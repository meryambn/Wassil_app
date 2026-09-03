package com.example.wassilapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "collaborative_delivery.db";
    // v3: added users.supabase_uid, orders.delivery_uid, orders.sync_status so the
    // local cache can represent cloud-owned state. New columns are declared LAST in
    // onCreate so their column index matches what ALTER TABLE ADD COLUMN produces on
    // upgrade — the cursor reads below are positional and would otherwise diverge
    // between a fresh install and an upgraded one.
    private static final int DB_VERSION = 3;

    // Users table
    private static final String TABLE_USERS = "users";
    private static final String COL_ID = "id";
    private static final String COL_FULL_NAME = "full_name";
    private static final String COL_PHONE = "phone";
    private static final String COL_EMAIL = "email";
    private static final String COL_ROLE = "role";
    private static final String COL_BALANCE = "balance";
    private static final String COL_VEHICLE_TYPE = "vehicle_type";
    private static final String COL_RATING = "rating";
    private static final String COL_WILAYA = "wilaya";
    private static final String COL_COMMUNE = "commune";
    private static final String COL_IS_ACTIVE = "is_active";
    private static final String COL_PROFILE_IMAGE = "profile_image";
    private static final String COL_SUPABASE_UID = "supabase_uid"; // v3, must stay last

    // Orders table
    private static final String TABLE_ORDERS = "orders";
    private static final String COL_ORDER_ID = "order_id";
    private static final String COL_SENDER_ID = "sender_id";
    private static final String COL_SENDER_NAME = "sender_name";
    private static final String COL_DELIVERY_ID = "delivery_id";
    private static final String COL_DELIVERY_NAME = "delivery_name";
    private static final String COL_STATUS = "status";
    private static final String COL_PRICE = "price";
    private static final String COL_NEGOTIATED_PRICE = "negotiated_price";
    private static final String COL_PICKUP_ADDRESS = "pickup_address";
    private static final String COL_DROP_ADDRESS = "drop_address";
    private static final String COL_PICKUP_WILAYA = "pickup_wilaya";
    private static final String COL_DROP_WILAYA = "drop_wilaya";
    private static final String COL_DISTANCE = "distance";
    private static final String COL_ESTIMATED_TIME = "estimated_time";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_PICKED_UP_AT = "picked_up_at";
    private static final String COL_DELIVERED_AT = "delivered_at";
    private static final String COL_SENDER_LAT = "sender_lat";
    private static final String COL_SENDER_LNG = "sender_lng";
    private static final String COL_DELIVERY_LAT = "delivery_lat";
    private static final String COL_DELIVERY_LNG = "delivery_lng";
    private static final String COL_PACKAGE_TYPE = "package_type";
    private static final String COL_WEIGHT = "weight";
    private static final String COL_SPECIAL_INSTRUCTIONS = "special_instructions";
    // v3, must stay last (see DB_VERSION note)
    private static final String COL_DELIVERY_UID = "delivery_uid";
    private static final String COL_SYNC_STATUS = "sync_status";

    private final Context context;
    private static final String PREF_COMMISSION_PERCENTAGE = "pref_commission_percentage";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public double getCommissionPercentage() {
        if (context == null) return 15.0;
        android.content.SharedPreferences prefs = context.getSharedPreferences("wassil_settings", Context.MODE_PRIVATE);
        return prefs.getFloat(PREF_COMMISSION_PERCENTAGE, 15.0f);
    }

    public void setCommissionPercentage(double rate) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("wassil_settings", Context.MODE_PRIVATE);
        prefs.edit().putFloat(PREF_COMMISSION_PERCENTAGE, (float) rate).apply();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Users Table
        String createUsers = "CREATE TABLE " + TABLE_USERS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_FULL_NAME + " TEXT, " + COL_PHONE + " TEXT UNIQUE, " +
                COL_EMAIL + " TEXT, " + COL_ROLE + " TEXT, " +
                COL_BALANCE + " REAL, " + COL_VEHICLE_TYPE + " TEXT, " +
                COL_RATING + " REAL, " + COL_WILAYA + " TEXT, " +
                COL_COMMUNE + " TEXT, " + COL_IS_ACTIVE + " INTEGER, " +
                COL_PROFILE_IMAGE + " TEXT, " + COL_SUPABASE_UID + " TEXT)";
        db.execSQL(createUsers);

        // Create Orders Table
        String createOrders = "CREATE TABLE " + TABLE_ORDERS + " (" +
                COL_ORDER_ID + " TEXT PRIMARY KEY, " + COL_SENDER_ID + " INTEGER, " +
                COL_SENDER_NAME + " TEXT, " + COL_DELIVERY_ID + " INTEGER, " +
                COL_DELIVERY_NAME + " TEXT, " + COL_STATUS + " TEXT, " +
                COL_PRICE + " REAL, " + COL_NEGOTIATED_PRICE + " REAL, " +
                COL_PICKUP_ADDRESS + " TEXT, " + COL_DROP_ADDRESS + " TEXT, " +
                COL_PICKUP_WILAYA + " TEXT, " + COL_DROP_WILAYA + " TEXT, " +
                COL_DISTANCE + " REAL, " + COL_ESTIMATED_TIME + " INTEGER, " +
                COL_CREATED_AT + " TEXT, " + COL_PICKED_UP_AT + " TEXT, " +
                COL_DELIVERED_AT + " TEXT, " + COL_SENDER_LAT + " REAL, " +
                COL_SENDER_LNG + " REAL, " + COL_DELIVERY_LAT + " REAL, " +
                COL_DELIVERY_LNG + " REAL, " + COL_PACKAGE_TYPE + " TEXT, " +
                COL_WEIGHT + " REAL, " + COL_SPECIAL_INSTRUCTIONS + " TEXT, " +
                COL_DELIVERY_UID + " TEXT, " +
                COL_SYNC_STATUS + " TEXT DEFAULT 'synced')";
        db.execSQL(createOrders);

        // NO DEMO DATA - Database starts empty
    }

    // User methods
    public long addUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_FULL_NAME, user.getFullName());
        values.put(COL_PHONE, user.getPhone());
        values.put(COL_EMAIL, user.getEmail());
        values.put(COL_ROLE, user.getRole());
        values.put(COL_BALANCE, user.getBalance());
        values.put(COL_VEHICLE_TYPE, user.getVehicleType());
        values.put(COL_RATING, user.getRating());
        values.put(COL_WILAYA, user.getWilaya());
        values.put(COL_COMMUNE, user.getCommune());
        values.put(COL_IS_ACTIVE, user.isActive() ? 1 : 0);
        return db.insert(TABLE_USERS, null, values);
    }

    private User cursorToUser(Cursor cursor) {
        User user = new User(
                cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getString(4), cursor.getDouble(5),
                cursor.getString(6), cursor.getDouble(7), cursor.getString(8),
                cursor.getString(9), cursor.getInt(10) == 1, cursor.getString(11)
        );
        int uidIndex = cursor.getColumnIndex(COL_SUPABASE_UID);
        if (uidIndex != -1) {
            user.setSupabaseUid(cursor.getString(uidIndex));
        }
        return user;
    }

    public User getUserByPhone(String phone) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, COL_PHONE + "=?",
                new String[]{phone}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            User user = cursorToUser(cursor);
            cursor.close();
            return user;
        }
        return null;
    }

    public User getUserById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, COL_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            User user = cursorToUser(cursor);
            cursor.close();
            return user;
        }
        return null;
    }

    /** Resolves a Supabase profiles.id to the local user row, or null if unknown here. */
    public User getUserBySupabaseUid(String supabaseUid) {
        if (supabaseUid == null) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, COL_SUPABASE_UID + "=?",
                new String[]{supabaseUid}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            User user = cursorToUser(cursor);
            cursor.close();
            return user;
        }
        if (cursor != null) {
            cursor.close();
        }
        return null;
    }

    /**
     * Ensures a local row exists for a cloud profile (e.g. the courier who took my
     * order, who has never logged in on this device). Returns the local int id, which
     * is what the rest of the app still joins on.
     */
    public int upsertUserFromCloud(String supabaseUid, String fullName, String phone,
                                    String role, String vehicleType, double rating) {
        User existing = getUserBySupabaseUid(supabaseUid);
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_FULL_NAME, fullName);
        values.put(COL_PHONE, phone);
        values.put(COL_ROLE, role);
        values.put(COL_VEHICLE_TYPE, vehicleType);
        values.put(COL_RATING, rating);
        values.put(COL_SUPABASE_UID, supabaseUid);

        if (existing != null) {
            db.update(TABLE_USERS, values, COL_ID + "=?",
                    new String[]{String.valueOf(existing.getId())});
            return existing.getId();
        }
        values.put(COL_IS_ACTIVE, 1);
        values.put(COL_BALANCE, 0.0);
        return (int) db.insert(TABLE_USERS, null, values);
    }

    /** Attaches a Supabase identity to an existing local row (used at login). */
    public void linkUserToSupabase(int localUserId, String supabaseUid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUPABASE_UID, supabaseUid);
        db.update(TABLE_USERS, values, COL_ID + "=?", new String[]{String.valueOf(localUserId)});
    }

    public List<User> getNearbyDeliveries(String wilaya) {
        List<User> deliveries = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null,
                COL_ROLE + "=? AND " + COL_IS_ACTIVE + "=?",
                new String[]{"delivery", "1"}, null, null, null);
        while (cursor.moveToNext()) {
            deliveries.add(cursorToUser(cursor));
        }
        cursor.close();
        return deliveries;
    }

    public void updateUserBalance(int userId, double newBalance) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_BALANCE, newBalance);
        db.update(TABLE_USERS, values, COL_ID + "=?", new String[]{String.valueOf(userId)});
    }

    public void updateDeliveryRating(int deliveryId, double newRating) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_RATING, newRating);
        db.update(TABLE_USERS, values, COL_ID + "=?", new String[]{String.valueOf(deliveryId)});
    }

    public boolean updateUserProfile(int userId, String fullName, String phone, String email, String wilaya, String commune) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_FULL_NAME, fullName);
        values.put(COL_PHONE, phone);
        values.put(COL_EMAIL, email);
        values.put(COL_WILAYA, wilaya);
        values.put(COL_COMMUNE, commune);

        int result = db.update(TABLE_USERS, values, COL_ID + "=?", new String[]{String.valueOf(userId)});
        return result > 0;
    }

    // Order methods

    /**
     * Inserts (or replaces) a local order row.
     *
     * <p>If {@code order} already carries an ID — i.e. the ID Supabase assigned —
     * that ID is reused so the local row and the cloud row share one identity.
     * Only orders that never reached the cloud get a locally-generated ID.
     * Without this, the same logical order had two different IDs and any
     * lookup by cloud ID (OrderTrackingActivity, OrderDetailsActivity) missed.
     */
    public String createOrder(Order order) {
        SQLiteDatabase db = this.getWritableDatabase();
        String orderId = (order.getOrderId() != null && !order.getOrderId().isEmpty())
                ? order.getOrderId()
                : generateLocalOrderId();

        ContentValues values = new ContentValues();
        values.put(COL_ORDER_ID, orderId);
        values.put(COL_SENDER_ID, order.getSenderId());
        values.put(COL_SENDER_NAME, order.getSenderName());
        values.put(COL_DELIVERY_ID, order.getDeliveryId());
        values.put(COL_DELIVERY_NAME, order.getDeliveryName());
        values.put(COL_STATUS, "pending");
        values.put(COL_PRICE, order.getPrice());
        values.put(COL_NEGOTIATED_PRICE, order.getPrice());
        values.put(COL_PICKUP_ADDRESS, order.getPickupAddress());
        values.put(COL_DROP_ADDRESS, order.getDropAddress());
        values.put(COL_PICKUP_WILAYA, order.getPickupWilaya());
        values.put(COL_DROP_WILAYA, order.getDropWilaya());
        values.put(COL_DISTANCE, order.getDistance());
        values.put(COL_ESTIMATED_TIME, order.getEstimatedTime());
        values.put(COL_CREATED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        values.put(COL_PACKAGE_TYPE, order.getPackageType());
        values.put(COL_WEIGHT, order.getWeight());
        values.put(COL_SPECIAL_INSTRUCTIONS, order.getSpecialInstructions());
        values.put(COL_SENDER_LAT, 0.0);
        values.put(COL_SENDER_LNG, 0.0);
        values.put(COL_DELIVERY_LAT, 0.0);
        values.put(COL_DELIVERY_LNG, 0.0);
        values.put(COL_PICKED_UP_AT, "");
        values.put(COL_DELIVERED_AT, "");

        long result = db.insert(TABLE_ORDERS, null, values);

        // Vérifier si l'insertion a réussi
        if (result != -1) {
            return orderId;
        } else {
            return null;
        }
    }

    /**
     * Writes cloud-owned order state into the local cache.
     *
     * <p>Distinct from {@link #createOrder} on purpose: createOrder has creation
     * semantics and hardcodes status='pending', so reusing it for sync would reset
     * the status of any order that had already advanced. This updates the fields
     * Supabase is authoritative for, and only inserts a full row when the order
     * doesn't exist locally at all (i.e. it was created on another device).
     *
     * @param deliveryUid the courier's Supabase id, or null if unassigned
     */
    public void upsertOrderFromCloud(Order order, String deliveryUid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, order.getStatus());
        values.put(COL_PRICE, order.getPrice());
        values.put(COL_NEGOTIATED_PRICE, order.getNegotiatedPrice());
        values.put(COL_DELIVERY_ID, order.getDeliveryId());
        values.put(COL_DELIVERY_NAME, order.getDeliveryName());
        values.put(COL_DELIVERY_UID, deliveryUid);
        values.put(COL_PICKED_UP_AT, order.getPickedUpAt());
        values.put(COL_DELIVERED_AT, order.getDeliveredAt());
        values.put(COL_SYNC_STATUS, "synced");

        int updated = db.update(TABLE_ORDERS, values, COL_ORDER_ID + "=?",
                new String[]{order.getOrderId()});
        if (updated > 0) {
            return;
        }

        values.put(COL_ORDER_ID, order.getOrderId());
        values.put(COL_SENDER_ID, order.getSenderId());
        values.put(COL_SENDER_NAME, order.getSenderName());
        values.put(COL_PICKUP_ADDRESS, order.getPickupAddress());
        values.put(COL_DROP_ADDRESS, order.getDropAddress());
        values.put(COL_PICKUP_WILAYA, order.getPickupWilaya());
        values.put(COL_DROP_WILAYA, order.getDropWilaya());
        values.put(COL_DISTANCE, order.getDistance());
        values.put(COL_ESTIMATED_TIME, order.getEstimatedTime());
        values.put(COL_CREATED_AT, order.getCreatedAt());
        values.put(COL_PACKAGE_TYPE, order.getPackageType());
        values.put(COL_WEIGHT, order.getWeight());
        values.put(COL_SPECIAL_INSTRUCTIONS, order.getSpecialInstructions());
        values.put(COL_SENDER_LAT, order.getSenderLatitude());
        values.put(COL_SENDER_LNG, order.getSenderLongitude());
        values.put(COL_DELIVERY_LAT, order.getDeliveryLatitude());
        values.put(COL_DELIVERY_LNG, order.getDeliveryLongitude());
        db.insert(TABLE_ORDERS, null, values);
    }

    /**
     * ID for orders that only ever existed locally. Uses Calendar rather than
     * java.time.Year, which needs API 26 while this app's minSdk is 24.
     */
    private String generateLocalOrderId() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        return "WSL-" + year + "-" +
                UUID.randomUUID().toString().substring(0, 5).toUpperCase();
    }

    public List<Order> getOrdersBySender(int senderId) {
        List<Order> orders = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDERS, null, COL_SENDER_ID + "=?",
                new String[]{String.valueOf(senderId)}, null, null,
                COL_CREATED_AT + " DESC");
        while (cursor.moveToNext()) {
            orders.add(cursorToOrder(cursor));
        }
        cursor.close();
        return orders;
    }

    public List<Order> getOrdersByDelivery(int deliveryId) {
        List<Order> orders = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDERS, null, COL_DELIVERY_ID + "=?",
                new String[]{String.valueOf(deliveryId)}, null, null,
                COL_CREATED_AT + " DESC");
        while (cursor.moveToNext()) {
            orders.add(cursorToOrder(cursor));
        }
        cursor.close();
        return orders;
    }

    public List<Order> getPendingOrders(String wilaya) {
        List<Order> orders = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDERS, null,
                COL_STATUS + "=? AND " + COL_PICKUP_WILAYA + "=?",
                new String[]{"pending", wilaya}, null, null,
                COL_CREATED_AT + " DESC");
        while (cursor.moveToNext()) {
            orders.add(cursorToOrder(cursor));
        }
        cursor.close();
        return orders;
    }

    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDERS, null, null, null, null, null,
                COL_CREATED_AT + " DESC");
        while (cursor.moveToNext()) {
            orders.add(cursorToOrder(cursor));
        }
        cursor.close();
        return orders;
    }

    public Order getOrderById(String orderId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDERS, null, COL_ORDER_ID + "=?",
                new String[]{orderId}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            Order order = cursorToOrder(cursor);
            cursor.close();
            return order;
        }
        return null;
    }

    public void updateOrderStatus(String orderId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, status);
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Mirrors the stamp_order_timestamps trigger: 'colis_recupere' is now the
        // moment the parcel is actually in the courier's hands. 'en_route' is kept as
        // a fallback for orders that skip the step, exactly as the trigger does — if
        // these two disagreed, the local cache and the cloud would show different
        // pickup times for the same delivery.
        if (status.equals("colis_recupere") || status.equals("en_route")) {
            values.put(COL_PICKED_UP_AT, currentDate);
        } else if (status.equals("livre")) {
            values.put(COL_DELIVERED_AT, currentDate);
        }
        db.update(TABLE_ORDERS, values, COL_ORDER_ID + "=?", new String[]{orderId});
    }

    public void assignDeliveryToOrder(String orderId, int deliveryId, String deliveryName, double negotiatedPrice) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DELIVERY_ID, deliveryId);
        values.put(COL_DELIVERY_NAME, deliveryName);
        values.put(COL_NEGOTIATED_PRICE, negotiatedPrice);
        values.put(COL_STATUS, "prise_en_charge");
        db.update(TABLE_ORDERS, values, COL_ORDER_ID + "=?", new String[]{orderId});
    }

    private Order cursorToOrder(Cursor cursor) {
        return new Order(
                cursor.getString(0),  // orderId
                cursor.getInt(1),     // senderId
                cursor.getString(2),  // senderName
                cursor.getInt(3),     // deliveryId
                cursor.getString(4),  // deliveryName
                cursor.getString(5),  // status
                cursor.getDouble(6),  // price
                cursor.getDouble(7),  // negotiatedPrice
                cursor.getString(8),  // pickupAddress
                cursor.getString(9),  // dropAddress
                cursor.getString(10), // pickupWilaya
                cursor.getString(11), // dropWilaya
                cursor.getDouble(12), // distance
                cursor.getInt(13),    // estimatedTime
                cursor.getString(14), // createdAt
                cursor.getString(15), // pickedUpAt
                cursor.getString(16), // deliveredAt
                cursor.getDouble(17), // senderLat
                cursor.getDouble(18), // senderLng
                cursor.getDouble(19), // deliveryLat
                cursor.getDouble(20), // deliveryLng
                cursor.getString(21), // packageType
                cursor.getDouble(22), // weight
                cursor.getString(23)  // specialInstructions
        );
    }

    // Statistics for admin
    public int getTotalOrders() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ORDERS, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public double getTotalRevenue() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_NEGOTIATED_PRICE + ") FROM " +
                TABLE_ORDERS + " WHERE " + COL_STATUS + "='livre'", null);
        cursor.moveToFirst();
        double total = cursor.getDouble(0);
        cursor.close();
        return total;
    }

    public int getActiveDeliveries() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_USERS +
                " WHERE " + COL_ROLE + "='delivery' AND " + COL_IS_ACTIVE + "=1", null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migrations only — the previous drop-and-recreate destroyed all
        // local data on every schema change.
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COL_SUPABASE_UID + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_ORDERS + " ADD COLUMN " + COL_DELIVERY_UID + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_ORDERS + " ADD COLUMN " + COL_SYNC_STATUS
                    + " TEXT DEFAULT 'synced'");
        }
    }
}