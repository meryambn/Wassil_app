package com.example.wassilapp.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wassilapp.R;
import com.example.wassilapp.adapters.OrderAdapter;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.notifications.PushRegistration;
import androidx.appcompat.app.AlertDialog;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.OfferRepository;
import com.example.wassilapp.remote.OrderSyncManager;
import com.example.wassilapp.remote.dto.OfferDto;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.utils.SessionManager;
import java.util.List;
import java.util.Locale;

public class SenderHomeActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private User currentUser;
    private List<Order> activeOrders;
    private SessionManager session;
    private OrderSyncManager syncManager;
    private final OfferRepository offerRepository = new OfferRepository();

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender_home);
        // Asked here rather than at launch: the request makes sense once the
        // user has an account and something to be notified about.
        PushRegistration.ensurePermission(this);
        PushRegistration.syncToken(this);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        syncManager = new OrderSyncManager(this);

        // Check if user is logged in
        if (!session.isLoggedIn()) {
            // Create a default session for testing
            User defaultUser = db.getUserByPhone("0555123456");
            if (defaultUser == null) {
                // Create default user
                android.content.ContentValues values = new android.content.ContentValues();
                values.put("full_name", "Test User");
                values.put("phone", "0555123456");
                values.put("email", "test@example.com");
                values.put("role", "sender");
                values.put("balance", 5000.0);
                values.put("vehicle_type", "");
                values.put("rating", 0.0);
                values.put("wilaya", "Alger");
                values.put("commune", "Alger Centre");
                values.put("is_active", 1);
                values.put("profile_image", "");

                long id = db.getWritableDatabase().insert("users", null, values);
                if (id != -1) {
                    defaultUser = db.getUserByPhone("0555123456");
                }
            }

            if (defaultUser != null) {
                session.createLoginSession(defaultUser.getId(), defaultUser.getFullName(), defaultUser.getRole());
                currentUser = defaultUser;
            }
        } else {
            // Get logged in user
            int userId = session.getUserId();
            String suid = session.getSupabaseUid();
            if (suid != null && !suid.isEmpty()) {
                currentUser = db.getUserBySupabaseUid(suid);
            }
            if (currentUser == null && userId != -1) {
                currentUser = db.getUserById(userId);
            }
            // If local cache doesn't have this cloud user yet, add them so the activity doesn't fall back to an old user
            if (currentUser == null && session.getUserName() != null && !session.getUserName().isEmpty()) {
                int localId = db.upsertUserFromCloud(
                        suid != null ? suid : "sender-cloud",
                        session.getUserName(),
                        "0555000000",
                        "sender",
                        "",
                        5.0
                );
                currentUser = db.getUserById(localId);
            }
        }

        // Final check - if still null, create temporary user
        if (currentUser == null) {
            Toast.makeText(this, "Erreur: Impossible de charger l'utilisateur", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Header
        TextView tvHello = findViewById(R.id.tvHello);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvBalance = findViewById(R.id.tvBalance);

        tvHello.setText("Bonjour 👋");
        tvName.setText(currentUser.getFullName());
        tvBalance.setText(String.format("%.0f DA", currentUser.getBalance()));

        // Stats
        List<Order> allOrders = db.getOrdersBySender(currentUser.getId());
        long totalDeliveries = allOrders.stream().filter(o -> "livre".equals(o.getStatus())).count();
        double totalSpent = allOrders.stream().filter(o -> "livre".equals(o.getStatus()))
                .mapToDouble(Order::getNegotiatedPrice).sum();

        TextView tvTotalDeliveries = findViewById(R.id.tvTotalDeliveries);
        TextView tvTotalSpent = findViewById(R.id.tvTotalSpent);
        tvTotalDeliveries.setText(String.valueOf(totalDeliveries));
        tvTotalSpent.setText(String.format("%.0f DA", totalSpent));

        // Active orders (not delivered)
        activeOrders = db.getOrdersBySender(currentUser.getId());
        activeOrders.removeIf(o -> "livre".equals(o.getStatus()));

        RecyclerView recyclerOrders = findViewById(R.id.recyclerActiveOrders);
        recyclerOrders.setLayoutManager(new LinearLayoutManager(this));
        OrderAdapter adapter = new OrderAdapter(activeOrders, this, "sender");
        // A still-pending order has no courier to track yet — tapping it should show
        // the couriers who bid, so the sender can choose one (the spec's core flow).
        adapter.setOnOrderClickListener(order -> {
            if ("pending".equals(order.getStatus())) {
                showOffersDialog(order);
            } else {
                Intent trackIntent = new Intent(this, OrderTrackingActivity.class);
                trackIntent.putExtra("order_id", order.getOrderId());
                trackIntent.putExtra("user_type", "sender");
                startActivity(trackIntent);
            }
        });
        recyclerOrders.setAdapter(adapter);

        // New delivery button
        Button btnNewDelivery = findViewById(R.id.btnNewDelivery);
        btnNewDelivery.setOnClickListener(v -> {
            Intent intent = new Intent(this, NewDeliveryActivity.class);
            startActivity(intent);
        });

        // Bottom navigation
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            // Already on home
        });
        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btnProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        // AI Virtual Assistant (FAB & Banner)
        View fabAi = findViewById(R.id.fabAiAssistant);
        if (fabAi != null) {
            fabAi.setOnClickListener(v -> startActivity(new Intent(this, AiAssistantActivity.class)));
        }

        View bannerAi = findViewById(R.id.cardAiAssistantBanner);
        if (bannerAi != null) {
            bannerAi.setOnClickListener(v -> startActivity(new Intent(this, AiAssistantActivity.class)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (currentUser != null && session.isLoggedIn()) {
            // Refresh data
            currentUser = db.getUserById(currentUser.getId());
            if (currentUser != null) {
                refreshOrdersFromCache();
                // Then pull authoritative state from Supabase — a courier may have
                // taken or advanced one of these orders on another device — and
                // re-read once it lands.
                syncManager.syncMyOrders(session.getSupabaseUid(), currentUser.getId(),
                        currentUser.getFullName(), new OrderSyncManager.SyncCallback() {
                            @Override
                            public void onSynced(int orderCount) {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                refreshOrdersFromCache();
                            }

                            @Override
                            public void onError(String message) {
                                // Cache already rendered above; nothing further to do.
                            }
                        });
            }
        }
    }

    /** Lists the couriers who proposed a price, cheapest first, and lets the sender pick. */
    private void showOffersDialog(Order order) {
        offerRepository.getPendingOffers(order.getOrderId(),
                new OfferRepository.OfferListCallback() {
                    @Override
                    public void onSuccess(List<OfferDto> offers) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (offers.isEmpty()) {
                            Toast.makeText(SenderHomeActivity.this,
                                    "Aucune offre pour le moment — patientez, les livreurs vont proposer un prix",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        String[] labels = new String[offers.size()];
                        for (int i = 0; i < offers.size(); i++) {
                            OfferDto o = offers.get(i);
                            String name = o.courier != null ? o.courier.full_name : "Livreur";
                            String vehicle = o.courier != null && o.courier.vehicle_type != null
                                    ? o.courier.vehicle_type : "—";
                            double rating = o.courier != null ? o.courier.rating : 0;
                            int reviews = o.courier != null ? o.courier.rating_count : 0;
                            // A courier with no reviews defaults to 5.0, so showing the
                            // score alone made a brand new livreur look identical to a
                            // proven one — precisely the comparison being made here.
                            String score = reviews == 0
                                    ? "nouveau"
                                    : String.format(Locale.getDefault(), "⭐ %.1f (%d avis)",
                                            rating, reviews);
                            labels[i] = String.format(Locale.getDefault(),
                                    "%.0f DA — %s (%s) %s",
                                    o.offered_price, name, vehicle, score);
                        }

                        new AlertDialog.Builder(SenderHomeActivity.this)
                                .setTitle("Choisissez votre livreur")
                                .setItems(labels, (dialog, which) -> acceptOffer(offers.get(which)))
                                .setNegativeButton("Annuler", null)
                                .show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(SenderHomeActivity.this,
                                "Impossible de charger les offres: " + message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void acceptOffer(OfferDto offer) {
        offerRepository.acceptOffer(offer.id, new OfferRepository.AcceptCallback() {
            @Override
            public void onAccepted(OrderDto updated) {
                Toast.makeText(SenderHomeActivity.this,
                        "Livreur choisi ! Prix: " + (int) offer.offered_price + " DA",
                        Toast.LENGTH_LONG).show();
                onResume(); // re-sync so the order shows its new status and courier
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SenderHomeActivity.this,
                        "Impossible d'accepter l'offre: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshOrdersFromCache() {
        if (currentUser == null || activeOrders == null) {
            return;
        }
        activeOrders.clear();
        activeOrders.addAll(db.getOrdersBySender(currentUser.getId()));
        activeOrders.removeIf(o -> "livre".equals(o.getStatus()));
        RecyclerView recyclerOrders = findViewById(R.id.recyclerActiveOrders);
        if (recyclerOrders != null && recyclerOrders.getAdapter() != null) {
            recyclerOrders.getAdapter().notifyDataSetChanged();
        }
    }
}