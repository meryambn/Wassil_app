package com.example.wassilapp.activities;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.adapters.WithdrawalAdapter;
import com.example.wassilapp.remote.WithdrawalRepository;
import com.example.wassilapp.remote.dto.WithdrawalDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin screen for approving, rejecting and settling courier withdrawals
 * (cahier des charges p.7 — "Paiements / Retraits").
 *
 * <p>Architecture, and the important part to understand:
 *
 * <pre>
 *   AdminWithdrawalsActivity   (this file — shows data, collects input)
 *          ↓
 *   WithdrawalRepository       (hides HTTP from the screen)
 *          ↓
 *   WithdrawalsApi             (Retrofit declarations)
 *          ↓
 *   Supabase RPC               resolve_withdrawal / mark_withdrawal_paid
 *          ↓
 *   PostgreSQL transaction     is_admin() + FOR UPDATE + status checks
 * </pre>
 *
 * <p>This Activity performs NO financial logic. It never computes a balance, never
 * decides whether an action is allowed, and never writes to the withdrawals table.
 * It asks the server and displays what comes back. The buttons it hides are a
 * convenience — an attacker launching this screen directly, or calling the API with
 * curl, is stopped by is_admin() inside the database, not by anything here.
 */
public class AdminWithdrawalsActivity extends AppCompatActivity
        implements WithdrawalAdapter.ActionListener {

    private final WithdrawalRepository repository = new WithdrawalRepository();
    private final List<WithdrawalDto> withdrawals = new ArrayList<>();
    private WithdrawalAdapter adapter;
    private TextView tvSummary, tvEmpty;
    /** Stops a second action starting while one is still on the wire. */
    private boolean actionInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_withdrawals);

        tvSummary = findViewById(R.id.tvWithdrawalsSummary);
        tvEmpty = findViewById(R.id.tvWithdrawalsEmpty);
        findViewById(R.id.btnBackWithdrawals).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recyclerWithdrawals);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WithdrawalAdapter(withdrawals, this, this);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        repository.getAllWithdrawals(new WithdrawalRepository.HistoryCallback() {
            @Override
            public void onSuccess(List<WithdrawalDto> list) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                withdrawals.clear();
                withdrawals.addAll(list);
                adapter.update(withdrawals);
                renderSummary();
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(AdminWithdrawalsActivity.this,
                            "Chargement impossible: " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Shows how much is still owed — the number the payout operator cares about. */
    private void renderSummary() {
        int pending = 0;
        double owed = 0;
        for (WithdrawalDto w : withdrawals) {
            if ("pending".equals(w.status) || "approved".equals(w.status)) {
                owed += w.amount;
                if ("pending".equals(w.status)) {
                    pending++;
                }
            }
        }
        tvSummary.setText(String.format(Locale.getDefault(),
                "%d en attente · %,.0f DA à verser · %d au total",
                pending, owed, withdrawals.size()));
        tvEmpty.setVisibility(withdrawals.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- actions coming from the adapter -------------------------------------

    @Override
    public void onApprove(WithdrawalDto w) {
        confirm("Approuver le retrait",
                String.format(Locale.getDefault(),
                        "Approuver %,.0f DA pour %s ?\n\nL'argent est déjà réservé ; "
                                + "il restera à effectuer le virement réel.",
                        w.amount, courierName(w)),
                () -> runAction(() -> repository.resolve(w.id, true, null, actionCallback("Retrait approuvé"))));
    }

    @Override
    public void onReject(WithdrawalDto w) {
        // A reason is genuinely useful here: rejecting refunds the courier, and they
        // will want to know why. The server stores it in withdrawals.note.
        EditText input = new EditText(this);
        input.setHint("Motif du rejet (optionnel)");

        new AlertDialog.Builder(this)
                .setTitle("Rejeter le retrait")
                .setMessage(String.format(Locale.getDefault(),
                        "Rejeter %,.0f DA ?\n\nLe montant sera recrédité au livreur.", w.amount))
                .setView(input)
                .setPositiveButton("Rejeter", (d, i) -> runAction(() ->
                        repository.resolve(w.id, false, input.getText().toString(),
                                actionCallback("Retrait rejeté et remboursé"))))
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public void onMarkPaid(WithdrawalDto w) {
        // The database refuses a blank reference, so this field is mandatory in
        // practice — the dialog just surfaces that rule earlier.
        EditText input = new EditText(this);
        input.setHint("Réf. du virement (CCP / BaridiMob / banque)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Marquer comme payé")
                .setMessage(String.format(Locale.getDefault(),
                        "Confirmez-vous avoir versé %,.0f DA à %s ?", w.amount, courierName(w)))
                .setView(input)
                .setPositiveButton("Confirmer", (d, i) -> {
                    String ref = input.getText().toString().trim();
                    if (ref.isEmpty()) {
                        Toast.makeText(this, "Référence de paiement obligatoire",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runAction(() -> repository.markPaid(w.id, ref, actionCallback("Retrait marqué payé")));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /**
     * Abandons an approved payout. This is NOT for a transfer that merely failed
     * once — in that case nothing needs doing here and "Marquer payé" can simply be
     * retried later. Cancelling means giving up, which is why it refunds.
     */
    @Override
    public void onCancel(WithdrawalDto w) {
        EditText input = new EditText(this);
        input.setHint("Raison (ex: virement impossible, RIB invalide)");

        new AlertDialog.Builder(this)
                .setTitle("Annuler le retrait")
                .setMessage(String.format(Locale.getDefault(),
                        "Abandonner le versement de %,.0f DA à %s ?\n\n"
                                + "Le montant sera recrédité au livreur.\n\n"
                                + "Si le virement a seulement échoué une fois, n'annulez pas : "
                                + "réessayez « Marquer payé » plus tard.",
                        w.amount, courierName(w)))
                .setView(input)
                .setPositiveButton("Annuler le retrait", (d, i) -> runAction(() ->
                        repository.cancel(w.id, input.getText().toString(),
                                actionCallback("Retrait annulé et remboursé"))))
                .setNegativeButton("Retour", null)
                .show();
    }

    // ---- plumbing -------------------------------------------------------------

    private void runAction(Runnable action) {
        if (actionInFlight) {
            return;
        }
        actionInFlight = true;
        action.run();
    }

    private WithdrawalRepository.ActionCallback actionCallback(String successMessage) {
        return new WithdrawalRepository.ActionCallback() {
            @Override
            public void onSuccess(WithdrawalDto updated) {
                actionInFlight = false;
                Toast.makeText(AdminWithdrawalsActivity.this, successMessage, Toast.LENGTH_SHORT).show();
                // Reload from the server rather than patching the row locally: the
                // database is the source of truth for what actually happened.
                load();
            }

            @Override
            public void onError(String message) {
                actionInFlight = false;
                // The server's own French reason (e.g. "Retrait déjà traité").
                Toast.makeText(AdminWithdrawalsActivity.this, message, Toast.LENGTH_LONG).show();
                load();
            }
        };
    }

    private void confirm(String title, String message, Runnable onYes) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Confirmer", (d, i) -> onYes.run())
                .setNegativeButton("Annuler", null)
                .show();
    }

    private String courierName(WithdrawalDto w) {
        return w.courier != null && w.courier.full_name != null ? w.courier.full_name : "ce livreur";
    }
}
