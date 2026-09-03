package com.example.wassilapp.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.adapters.KycReviewAdapter;
import com.example.wassilapp.remote.KycRepository;
import com.example.wassilapp.remote.dto.KycDocumentDto;

import java.util.ArrayList;
import java.util.List;

/**
 * The admin's identity-verification queue.
 *
 * <p>Security note, same shape as AdminWithdrawalsActivity: this screen filters
 * nothing that matters. The query asks for pending documents, and kyc_select
 * independently narrows the result to the caller's own rows unless is_admin() — so a
 * non-admin who reached this screen would simply see an empty list, and
 * review_kyc_document would refuse them anyway. Both were measured against real
 * tokens.
 */
public class AdminKycActivity extends AppCompatActivity implements KycReviewAdapter.Actions {

    private final KycRepository repository = new KycRepository();
    private final List<KycDocumentDto> pending = new ArrayList<>();

    private KycReviewAdapter adapter;
    private RecyclerView recycler;
    private View progress;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_kyc);

        recycler = findViewById(R.id.recyclerAdminKyc);
        progress = findViewById(R.id.progressAdminKyc);
        empty = findViewById(R.id.tvAdminKycEmpty);

        findViewById(R.id.btnBackAdminKyc).setOnClickListener(v -> finish());

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KycReviewAdapter(pending, this, this);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        repository.getPendingQueue(new KycRepository.ListCallback() {
            @Override
            public void onSuccess(List<KycDocumentDto> documents) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                adapter.update(documents);
                empty.setVisibility(documents.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(AdminKycActivity.this,
                            "Chargement impossible. " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onView(KycDocumentDto document) {
        // The bucket is private, so there is no lasting URL to open. A signed link is
        // minted per view and expires in five minutes, which keeps a screenshot or a
        // log line from becoming a permanent handle on somebody's ID card.
        repository.signedUrl(document.file_url, new KycRepository.SignedUrlCallback() {
            @Override
            public void onUrl(String absoluteUrl) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(absoluteUrl)));
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(AdminKycActivity.this,
                            "Document illisible. " + message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onDecide(KycDocumentDto document, boolean approve) {
        String who = document.owner != null && document.owner.full_name != null
                ? document.owner.full_name : "ce livreur";

        // Confirmed because it is irreversible: review_kyc_document refuses a document
        // that is no longer pending, so a mis-tap cannot simply be redone.
        new AlertDialog.Builder(this)
                .setTitle(approve ? "Valider le document" : "Refuser le document")
                .setMessage((approve ? "Valider" : "Refuser") + " le document de " + who
                        + " ? Cette décision est définitive.")
                .setPositiveButton("Confirmer", (d, w) -> submitDecision(document, approve))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void submitDecision(KycDocumentDto document, boolean approve) {
        repository.review(document.id, approve, new KycRepository.ReviewCallback() {
            @Override
            public void onReviewed(KycDocumentDto updated) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(AdminKycActivity.this,
                        approve ? "Document validé" : "Document refusé",
                        Toast.LENGTH_SHORT).show();
                // Re-read rather than mutate locally: the courier's overall status is
                // recomputed by a trigger, so the server is the only thing that knows
                // the result of this decision.
                load();
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(AdminKycActivity.this,
                            "Décision non enregistrée. " + message, Toast.LENGTH_LONG).show();
                    load();
                }
            }
        });
    }
}
