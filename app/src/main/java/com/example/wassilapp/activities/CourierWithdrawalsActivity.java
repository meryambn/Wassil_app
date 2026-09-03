package com.example.wassilapp.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.adapters.WithdrawalAdapter;
import com.example.wassilapp.remote.WithdrawalRepository;
import com.example.wassilapp.remote.dto.WithdrawalDto;
import com.example.wassilapp.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The courier's own withdrawal history — "Retraits" in the cahier des charges (p.6,
 * Livreur → Revenus : Historique / Solde / Retraits).
 *
 * <p>Strictly read-only. There is no code path here that changes a withdrawal, and
 * the adapter runs in read-only mode so no action button is ever inflated.
 *
 * <p>Security note: this screen does not filter anything itself in a way that could
 * be bypassed. It calls getMyWithdrawals() with the signed-in user's id, and the
 * withdrawals_select RLS policy independently restricts rows to
 * {@code courier_id = auth.uid()}. Even if this Activity asked for someone else's
 * withdrawals, the database would return nothing.
 *
 * <pre>
 *   CourierWithdrawalsActivity
 *          ↓
 *   WithdrawalRepository.getMyWithdrawals()
 *          ↓
 *   GET /rest/v1/withdrawals?courier_id=eq.&lt;uid&gt;
 *          ↓
 *   RLS: courier_id = auth.uid() OR is_admin()
 * </pre>
 */
public class CourierWithdrawalsActivity extends AppCompatActivity {

    private final WithdrawalRepository repository = new WithdrawalRepository();
    private final List<WithdrawalDto> withdrawals = new ArrayList<>();

    private WithdrawalAdapter adapter;
    private SessionManager session;
    private RecyclerView recycler;
    private View progress, errorBox;
    private TextView summary, empty, errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_courier_withdrawals);

        session = new SessionManager(this);

        summary = findViewById(R.id.tvRetraitsSummary);
        empty = findViewById(R.id.tvRetraitsEmpty);
        progress = findViewById(R.id.progressRetraits);
        errorBox = findViewById(R.id.llRetraitsError);
        errorText = findViewById(R.id.tvRetraitsError);
        recycler = findViewById(R.id.recyclerRetraits);

        findViewById(R.id.btnBackRetraits).setOnClickListener(v -> finish());
        findViewById(R.id.btnRetraitsRetry).setOnClickListener(v -> load());

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = WithdrawalAdapter.readOnly(withdrawals, this);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time the screen is shown: an admin may have approved, paid,
        // rejected or cancelled a request since the courier last looked.
        load();
    }

    private void load() {
        String uid = session.getSupabaseUid();
        if (uid == null) {
            showError("Reconnectez-vous pour consulter vos retraits.");
            return;
        }

        showLoading();
        repository.getMyWithdrawals(uid, new WithdrawalRepository.HistoryCallback() {
            @Override
            public void onSuccess(List<WithdrawalDto> list) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                withdrawals.clear();
                withdrawals.addAll(list);
                adapter.update(withdrawals);
                showContent();
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    showError("Impossible de charger vos retraits.\n" + message);
                }
            }
        });
    }

    // ---- the four screen states ----------------------------------------------

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        errorBox.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        summary.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        progress.setVisibility(View.GONE);
        errorBox.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        summary.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
    }

    private void showContent() {
        progress.setVisibility(View.GONE);
        errorBox.setVisibility(View.GONE);

        if (withdrawals.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            summary.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
            return;
        }

        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        summary.setVisibility(View.VISIBLE);
        summary.setText(buildSummary());
    }

    /**
     * How much is still on its way. 'pending' and 'approved' are money already taken
     * out of the balance but not yet received — the figure a courier most wants.
     */
    private String buildSummary() {
        double inProgress = 0;
        double received = 0;
        for (WithdrawalDto w : withdrawals) {
            if ("pending".equals(w.status) || "approved".equals(w.status)) {
                inProgress += w.amount;
            } else if ("paid".equals(w.status)) {
                received += w.amount;
            }
        }
        return String.format(Locale.getDefault(),
                "%,.0f DA en cours · %,.0f DA reçus · %d demande(s)",
                inProgress, received, withdrawals.size());
    }
}
