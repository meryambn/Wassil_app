package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wassilapp.R;
import com.example.wassilapp.adapters.DeliveryRequestAdapter;
import com.example.wassilapp.adapters.OrderAdapter;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.notifications.PushRegistration;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.OrderMapper;
import com.example.wassilapp.remote.OrderRepository;
import com.example.wassilapp.remote.OrderSyncManager;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.utils.SessionManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.view.View;
import android.widget.Toast;

public class DeliveryHomeActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private final OrderRepository orderRepository = new OrderRepository();
    private OrderSyncManager syncManager;
    private User currentUser;
    private RecyclerView recyclerRequests;
    private DeliveryRequestAdapter adapter;
    private RecyclerView recyclerActive;
    private OrderAdapter activeAdapter;
    private final List<Order> activeDeliveries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_home);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        syncManager = new OrderSyncManager(this);
        // Asked here rather than at launch: the request makes sense once the
        // user has an account and something to be notified about.
        PushRegistration.ensurePermission(this);
        PushRegistration.syncToken(this);

        currentUser = db.getUserById(session.getUserId());

        // Header
        TextView tvHello = findViewById(R.id.tvHelloDelivery);
        TextView tvTodayCount = findViewById(R.id.tvTodayCount);
        TextView tvTotalGains = findViewById(R.id.tvTotalGains);
        TextView tvRating = findViewById(R.id.tvRating);
        TextView tvDeliveryName = findViewById(R.id.tvDeliveryName);

        tvHello.setText("Bonjour 👋");
        tvDeliveryName.setText(currentUser.getFullName());
        tvRating.setText(String.valueOf(currentUser.getRating()));

        refreshStats();

        // Nearby delivery requests — Supabase is the source of truth for order
        // availability (shared across devices), so we don't pre-seed from SQLite
        // here: that would show stale/wrong availability for a frame or more.
        // onResume() (called right after onCreate) fetches the real list; SQLite
        // is only used as a fallback if that fetch fails — see onError below.
        recyclerRequests = findViewById(R.id.recyclerNearbyRequests);
        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeliveryRequestAdapter(new ArrayList<>(), this,
                currentUser.getId(), session.getSupabaseUid());
        recyclerRequests.setAdapter(adapter);

        // Courses en cours. OrderAdapter's item click opens OrderTrackingActivity with
        // user_type="delivery", which is what reveals the status-advance button — the
        // courier previously had no route to that screen at all.
        recyclerActive = findViewById(R.id.recyclerActiveDeliveries);
        recyclerActive.setLayoutManager(new LinearLayoutManager(this));
        activeAdapter = new OrderAdapter(activeDeliveries, this, "delivery");
        recyclerActive.setAdapter(activeAdapter);

        // Bottom navigation
        findViewById(R.id.btnHome).setOnClickListener(v -> {});

        findViewById(R.id.btnEarnings).setOnClickListener(v -> {
            Intent intent = new Intent(this, EarningsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        findViewById(R.id.btnProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });

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
        // Refresh data when returning to this activity
        currentUser = db.getUserById(currentUser.getId());
        TextView tvRating = findViewById(R.id.tvRating);
        tvRating.setText(String.valueOf(currentUser.getRating()));

        loadPendingRequestsFromCloud();

        // Pull my own assigned orders. The claim is written straight to Supabase, so
        // without this the local cache never learns about it and "courses en cours"
        // would stay empty even right after accepting a request.
        syncManager.syncMyOrders(session.getSupabaseUid(), currentUser.getId(),
                currentUser.getFullName(), new OrderSyncManager.SyncCallback() {
                    @Override
                    public void onSynced(int orderCount) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        refreshActiveDeliveries();
                        refreshStats();
                    }

                    @Override
                    public void onError(String message) {
                        // Fall back to whatever the cache already holds.
                        if (!isFinishing() && !isDestroyed()) {
                            refreshActiveDeliveries();
                            refreshStats();
                        }
                    }
                });
    }

    /** Orders I've accepted but not yet delivered. */
    private void refreshActiveDeliveries() {
        activeDeliveries.clear();
        for (Order o : db.getOrdersByDelivery(currentUser.getId())) {
            String s = o.getStatus();
            // Everything between acceptance and delivery counts as "in progress".
            // Listing the intermediate states explicitly (rather than "not livre")
            // keeps cancelled orders out of the courier's active list.
            if ("prise_en_charge".equals(s) || "vers_depart".equals(s)
                    || "colis_recupere".equals(s) || "en_route".equals(s)) {
                activeDeliveries.add(o);
            }
        }
        activeAdapter.notifyDataSetChanged();

        View empty = findViewById(R.id.tvNoActiveDeliveries);
        if (empty != null) {
            empty.setVisibility(activeDeliveries.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * "Aujourd'hui" / "Gains" count only deliveries actually completed today.
     * These previously summed every order ever assigned to the courier regardless of
     * status, so a freshly accepted job already showed up as earned money.
     */
    private void refreshStats() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int deliveredToday = 0;
        double earnedToday = 0;

        for (Order o : db.getOrdersByDelivery(currentUser.getId())) {
            if (!"livre".equals(o.getStatus())) {
                continue;
            }
            String at = o.getDeliveredAt();
            // Local writes format as "yyyy-MM-dd HH:mm:ss" while cloud rows arrive as
            // ISO-8601; both share the same first 10 characters.
            if (at != null && at.length() >= 10 && at.substring(0, 10).equals(today)) {
                deliveredToday++;
                earnedToday += o.getNegotiatedPrice();
            }
        }

        TextView tvTodayCount = findViewById(R.id.tvTodayCount);
        TextView tvTotalGains = findViewById(R.id.tvTotalGains);
        if (tvTodayCount != null) {
            tvTodayCount.setText(String.valueOf(deliveredToday));
        }
        if (tvTotalGains != null) {
            tvTotalGains.setText(String.format(Locale.getDefault(), "%.0f", earnedToday));
        }
    }

    private void loadPendingRequestsFromCloud() {
        orderRepository.getPending(currentUser.getWilaya(), new OrderRepository.OrderListCallback() {
            @Override
            public void onSuccess(List<OrderDto> orders) {
                List<Order> mapped = new ArrayList<>();
                for (OrderDto dto : orders) {
                    mapped.add(toLocalOrder(dto));
                }
                adapter.updateRequests(mapped);
                updateRequestsCount(mapped.size());
            }

            @Override
            public void onError(String message) {
                // Cloud fetch failed (offline, misconfigured account, etc.) — fall back
                // to the local cache. This data can be stale (an order shown here may
                // already be taken, or a newer one may be missing) since it wasn't
                // confirmed against the source of truth — say so explicitly.
                Toast.makeText(DeliveryHomeActivity.this,
                        "Hors ligne — affichage des données locales, qui peuvent être obsolètes",
                        Toast.LENGTH_LONG).show();
                List<Order> local = db.getPendingOrders(currentUser.getWilaya());
                adapter.updateRequests(local);
                updateRequestsCount(local.size());
            }
        });
    }

    private void updateRequestsCount(int count) {
        TextView tvRequestsCount = findViewById(R.id.tvRequestsCount);
        if (tvRequestsCount != null) {
            tvRequestsCount.setText(String.valueOf(count));
        }
    }

    private Order toLocalOrder(OrderDto dto) {
        // Pending orders are unassigned and belong to another user, so there are no
        // local identities to resolve here.
        return OrderMapper.toOrder(dto, 0, "", 0, "—");
    }
}