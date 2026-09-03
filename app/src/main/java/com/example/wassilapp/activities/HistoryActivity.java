package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wassilapp.R;
import com.example.wassilapp.adapters.OrderAdapter;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.OrderSyncManager;
import com.example.wassilapp.utils.SessionManager;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private User currentUser;
    private List<Order> orderHistory;
    private OrderSyncManager syncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        syncManager = new OrderSyncManager(this);
        currentUser = db.getUserById(session.getUserId());

        TextView tvTitle = findViewById(R.id.tvHistoryTitle);
        if (currentUser.getRole().equals("sender")) {
            tvTitle.setText("Historique des envois");
        } else {
            tvTitle.setText("Historique des livraisons");
        }

        loadHistory();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Bottom navigation
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            if (currentUser.getRole().equals("sender")) {
                startActivity(new Intent(this, SenderHomeActivity.class));
            } else {
                startActivity(new Intent(this, DeliveryHomeActivity.class));
            }
            finish();
        });

        findViewById(R.id.btnProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }

    private void loadHistory() {
        if (currentUser.getRole().equals("sender")) {
            orderHistory = db.getOrdersBySender(currentUser.getId());
        } else {
            orderHistory = db.getOrdersByDelivery(currentUser.getId());
        }

        // Filter only completed orders for history
        if (orderHistory != null) {
            orderHistory.removeIf(o -> !o.getStatus().equals("livre"));
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        OrderAdapter adapter = new OrderAdapter(orderHistory, this, currentUser.getRole());
        recyclerView.setAdapter(adapter);

        TextView tvEmpty = findViewById(R.id.tvEmptyHistory);
        if (orderHistory == null || orderHistory.isEmpty()) {
            tvEmpty.setVisibility(android.view.View.VISIBLE);
            recyclerView.setVisibility(android.view.View.GONE);
        } else {
            tvEmpty.setVisibility(android.view.View.GONE);
            recyclerView.setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // History is derived from status ('livre'), which only Supabase knows
        // authoritatively — a delivery completed on the courier's device would
        // otherwise never show up here.
        syncManager.syncMyOrders(session.getSupabaseUid(), currentUser.getId(),
                currentUser.getFullName(), new OrderSyncManager.SyncCallback() {
                    @Override
                    public void onSynced(int orderCount) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        loadHistory();
                    }

                    @Override
                    public void onError(String message) {
                        // Keep showing the cached history loaded in onCreate.
                    }
                });
    }

    private void navigateTo(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }
}