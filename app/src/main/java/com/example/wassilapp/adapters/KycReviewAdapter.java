package com.example.wassilapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.remote.dto.KycDocumentDto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One pending identity document per row, for the admin queue.
 *
 * <p>The buttons visible here are presentation only. review_kyc_document re-checks
 * is_admin() and refuses a document that is not still pending, so a stale screen
 * cannot approve anything twice.
 */
public class KycReviewAdapter extends RecyclerView.Adapter<KycReviewAdapter.ViewHolder> {

    public interface Actions {
        void onView(KycDocumentDto document);

        void onDecide(KycDocumentDto document, boolean approve);
    }

    private final List<KycDocumentDto> documents;
    private final Context context;
    private final Actions actions;

    public KycReviewAdapter(List<KycDocumentDto> documents, Context context, Actions actions) {
        this.documents = documents;
        this.context = context;
        this.actions = actions;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context)
                .inflate(R.layout.item_kyc_review, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KycDocumentDto d = documents.get(position);

        holder.tvOwner.setText(d.owner != null && d.owner.full_name != null
                ? d.owner.full_name : "Livreur");
        holder.tvType.setText(labelFor(d.doc_type));
        holder.tvSubmitted.setText("Envoyé le " + longDateFr(d.created_at));
        bindOcr(holder, d);

        // Rebound on every bind, not just when new: a recycled row would otherwise
        // keep the previous document's listener and act on the wrong record.
        holder.btnView.setOnClickListener(v -> actions.onView(d));
        holder.btnApprove.setOnClickListener(v -> actions.onDecide(d, true));
        holder.btnReject.setOnClickListener(v -> actions.onDecide(d, false));
    }

    /**
     * Shows what the phone read off the document, and whether the courier's profile
     * name appears in it.
     *
     * <p>Worded as an indication on purpose. The recognition happened on the
     * applicant's own device, so a determined applicant controls this text entirely —
     * it can speed up an obvious approval or draw attention to an odd one, but the
     * decision rests on the image the admin opens.
     */
    private void bindOcr(ViewHolder holder, KycDocumentDto d) {
        if (d.ocr_text == null || d.ocr_text.trim().isEmpty()) {
            holder.tvOcr.setVisibility(View.GONE);
            return;
        }
        holder.tvOcr.setVisibility(View.VISIBLE);

        String verdict;
        if (d.ocr_name_matches == null) {
            verdict = "Nom non vérifiable automatiquement";
        } else if (d.ocr_name_matches) {
            verdict = "✅ Le nom du profil apparaît sur le document";
        } else {
            verdict = "⚠️ Le nom du profil n'apparaît pas — à vérifier";
        }

        // OCR output is full of line breaks from the document layout; collapsing them
        // keeps the card readable.
        String snippet = d.ocr_text.replaceAll("\\s+", " ").trim();
        if (snippet.length() > 140) {
            snippet = snippet.substring(0, 140) + "…";
        }
        holder.tvOcr.setText(verdict + "\n\nTexte lu (indicatif) : " + snippet);
    }

    private String labelFor(String docType) {
        if (docType == null) return "Document";
        switch (docType) {
            case "cin": return "Carte nationale d'identité";
            case "permis": return "Permis de conduire";
            case "carte_grise": return "Carte grise";
            default: return docType;
        }
    }

    /** SimpleDateFormat rather than java.time: minSdk is 24, java.time needs 26. */
    private String longDateFr(String iso) {
        if (iso == null || iso.length() < 10) {
            return "";
        }
        String dayPart = iso.substring(0, 10);
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dayPart);
            return parsed == null ? dayPart
                    : new SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(parsed);
        } catch (ParseException e) {
            return dayPart;
        }
    }

    @Override
    public int getItemCount() {
        return documents != null ? documents.size() : 0;
    }

    public void update(List<KycDocumentDto> fresh) {
        documents.clear();
        documents.addAll(fresh);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOwner, tvType, tvSubmitted, tvOcr;
        Button btnView, btnApprove, btnReject;

        ViewHolder(View itemView) {
            super(itemView);
            tvOwner = itemView.findViewById(R.id.tvKycOwner);
            tvType = itemView.findViewById(R.id.tvKycDocType);
            tvSubmitted = itemView.findViewById(R.id.tvKycSubmitted);
            tvOcr = itemView.findViewById(R.id.tvKycOcr);
            btnView = itemView.findViewById(R.id.btnKycView);
            btnApprove = itemView.findViewById(R.id.btnKycApprove);
            btnReject = itemView.findViewById(R.id.btnKycReject);
        }
    }
}
