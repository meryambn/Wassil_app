package com.example.wassilapp.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.OrderSyncManager;
import com.example.wassilapp.remote.WithdrawalRepository;
import com.example.wassilapp.remote.dto.WithdrawalDto;
import com.example.wassilapp.utils.SessionManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EarningsActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private OrderSyncManager syncManager;
    private final WithdrawalRepository withdrawalRepository = new WithdrawalRepository();
    /** True while a withdrawal request is on the wire, so repeated taps can't stack. */
    private boolean withdrawalInFlight = false;
    private User currentUser;
    private TextView tvAvailableBalance, tvWeeklyEarnings, tvWeeklyDeliveries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earnings);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        syncManager = new OrderSyncManager(this);
        currentUser = db.getUserById(session.getUserId());

        // Find views
        tvAvailableBalance = findViewById(R.id.tvAvailableBalance);
        tvWeeklyEarnings = findViewById(R.id.tvWeeklyEarnings);
        tvWeeklyDeliveries = findViewById(R.id.tvWeeklyDeliveries);
        Button btnWithdraw = findViewById(R.id.btnWithdraw);
        Button btnBack = findViewById(R.id.btnBack);

        // Set balance
        tvAvailableBalance.setText(String.format("%,.0f DA", currentUser.getBalance()));

        // Calculate weekly stats
        calculateWeeklyStats();

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Withdraw button - Retirer l'argent
        btnWithdraw.setOnClickListener(v -> {
            showWithdrawDialog();
        });

        // Read-only history of this courier's withdrawal requests (spec p.6 Retraits).
        findViewById(R.id.btnMyWithdrawals).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CourierWithdrawalsActivity.class)));
    }

    private void calculateWeeklyStats() {
        List<Order> deliveries = db.getOrdersByDelivery(currentUser.getId());

        // Get date from 7 days ago
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String sevenDaysAgoStr = sdf.format(new Date(sevenDaysAgo));

        double weeklyTotal = 0;
        int weeklyCount = 0;

        for (Order order : deliveries) {
            if (order.getStatus().equals("livre")) {
                String orderDate = order.getDeliveredAt();
                if (orderDate != null && orderDate.length() >= 10) {
                    String dateOnly = orderDate.substring(0, 10);
                    if (dateOnly.compareTo(sevenDaysAgoStr) >= 0) {
                        weeklyTotal += order.getNegotiatedPrice();
                        weeklyCount++;
                    }
                }
            }
        }

        tvWeeklyEarnings.setText(String.format("%,.0f", weeklyTotal));
        tvWeeklyDeliveries.setText(String.valueOf(weeklyCount));
    }

    private void showWithdrawDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_withdraw, null);

        EditText etAmount = view.findViewById(R.id.etWithdrawAmount);
        TextView tvMaxAmount = view.findViewById(R.id.tvMaxAmount);

        double currentBalance = currentUser.getBalance();
        tvMaxAmount.setText(String.format("Solde maximum: %.0f DA", currentBalance));

        builder.setTitle("Retirer de l'argent")
                .setView(view)
                .setPositiveButton("Retirer", (dialog, which) -> {
                    String amountStr = etAmount.getText().toString();
                    if (amountStr.isEmpty()) {
                        Toast.makeText(this, "Entrez un montant", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double amount = Double.parseDouble(amountStr);

                    if (amount <= 0) {
                        Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (amount > currentBalance) {
                        Toast.makeText(this, "Solde insuffisant", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    submitWithdrawal(amount);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /**
     * Asks the SERVER to withdraw. The phone deliberately does not subtract anything
     * itself: the old code edited only the local cache, so the money reappeared as
     * soon as the next profile sync pulled the unchanged cloud balance back down.
     *
     * <p>The amount check above is a courtesy to the user only — the real check runs
     * inside the database function, which is the one that cannot be bypassed.
     */
    private void submitWithdrawal(double amount) {
        if (withdrawalInFlight) {
            return; // guard against repeated taps launching parallel requests
        }
        withdrawalInFlight = true;
        Toast.makeText(this, "Demande de retrait en cours…", Toast.LENGTH_SHORT).show();

        withdrawalRepository.requestWithdrawal(amount, new WithdrawalRepository.WithdrawalCallback() {
            @Override
            public void onRequested(WithdrawalDto withdrawal) {
                withdrawalInFlight = false;
                Toast.makeText(EarningsActivity.this,
                        String.format(Locale.getDefault(),
                                "Retrait de %.0f DA demandé (en attente de validation)", amount),
                        Toast.LENGTH_LONG).show();
                // Pull the new balance from the server rather than computing it here,
                // so the screen always shows what the database actually holds.
                refreshBalanceFromServer();
            }

            @Override
            public void onRejected(String reason) {
                withdrawalInFlight = false;
                Toast.makeText(EarningsActivity.this, reason, Toast.LENGTH_LONG).show();
                refreshBalanceFromServer();
            }

            @Override
            public void onError(String message) {
                withdrawalInFlight = false;
                // The request might have succeeded with only the reply lost, so we do
                // not claim it failed — we re-read the server and let it decide.
                Toast.makeText(EarningsActivity.this,
                        "Connexion échouée — vérification du solde…", Toast.LENGTH_LONG).show();
                refreshBalanceFromServer();
            }
        });
    }

    /** Re-syncs the profile so the displayed balance matches Supabase. */
    private void refreshBalanceFromServer() {
        syncManager.syncMyProfile(session.getSupabaseUid(), currentUser.getId(),
                new OrderSyncManager.SyncCallback() {
                    @Override
                    public void onSynced(int count) {
                        if (!isFinishing() && !isDestroyed()) {
                            renderFromCache();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Offline: the cached figure stays until the next successful sync.
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderFromCache();

        // The balance is credited server-side by the orders_credit_courier trigger
        // when a delivery completes, and deliveries may be completed from another
        // device — so both the profile and the orders need pulling.
        String uid = session.getSupabaseUid();
        syncManager.syncMyProfile(uid, currentUser.getId(), new OrderSyncManager.SyncCallback() {
            @Override
            public void onSynced(int count) {
                if (!isFinishing() && !isDestroyed()) {
                    renderFromCache();
                }
            }

            @Override
            public void onError(String message) {
                // Keep the cached balance on screen.
            }
        });

        syncManager.syncMyOrders(uid, currentUser.getId(), currentUser.getFullName(),
                new OrderSyncManager.SyncCallback() {
                    @Override
                    public void onSynced(int orderCount) {
                        if (!isFinishing() && !isDestroyed()) {
                            calculateWeeklyStats();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Keep the cached weekly stats.
                    }
                });
    }

    private void renderFromCache() {
        currentUser = db.getUserById(currentUser.getId());
        if (currentUser != null) {
            tvAvailableBalance.setText(String.format("%,.0f DA", currentUser.getBalance()));
        }
        calculateWeeklyStats();
    }
}