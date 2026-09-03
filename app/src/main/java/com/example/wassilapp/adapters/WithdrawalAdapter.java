package com.example.wassilapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.remote.dto.WithdrawalDto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Renders one withdrawal per row for the admin screen.
 *
 * <p>An "adapter" connects a list of data objects to a scrolling list of views:
 * onCreateViewHolder inflates a row layout, onBindViewHolder fills one row with one
 * item's data. RecyclerView reuses row views as you scroll, which is why every field
 * must be set on every bind — including setting things back to GONE, otherwise a
 * recycled row keeps the previous item's leftovers.
 *
 * <p>The visible buttons depend on the row's status. This is presentation only: the
 * database re-checks is_admin() and the status on every action, so a hidden button
 * is a convenience, never a security control.
 */
public class WithdrawalAdapter extends RecyclerView.Adapter<WithdrawalAdapter.ViewHolder> {

    /** The screen decides what actually happens; the adapter only reports the click. */
    public interface ActionListener {
        void onApprove(WithdrawalDto w);

        void onReject(WithdrawalDto w);

        void onMarkPaid(WithdrawalDto w);

        /** Abandon an approved payout and refund the courier. */
        void onCancel(WithdrawalDto w);
    }

    private List<WithdrawalDto> items;
    private final Context context;
    private final ActionListener listener;
    private final boolean readOnly;

    /** Admin mode: actions available. */
    public WithdrawalAdapter(List<WithdrawalDto> items, Context context, ActionListener listener) {
        this(items, context, listener, false);
    }

    /**
     * Courier mode: history only, no actions.
     *
     * <p>The same adapter serves both screens on purpose. The French status labels
     * and colours live in one place, so adding a status (as we did with 'cancelled')
     * cannot leave one screen showing a raw English value while the other is correct.
     */
    public static WithdrawalAdapter readOnly(List<WithdrawalDto> items, Context context) {
        return new WithdrawalAdapter(items, context, null, true);
    }

    private WithdrawalAdapter(List<WithdrawalDto> items, Context context,
                               ActionListener listener, boolean readOnly) {
        this.items = items;
        this.context = context;
        this.listener = listener;
        this.readOnly = readOnly;
    }

    public void update(List<WithdrawalDto> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_withdrawal_admin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        WithdrawalDto w = items.get(position);

        h.amount.setText(String.format(Locale.getDefault(), "%,.0f DA", w.amount));
        // A courier does not need to be told their own name, and getMyWithdrawals
        // does not embed the profile anyway, so hide the line entirely in read-only
        // mode rather than falling back to "Livreur inconnu".
        if (readOnly) {
            h.courier.setVisibility(View.GONE);
        } else {
            h.courier.setVisibility(View.VISIBLE);
            h.courier.setText(w.courier != null && w.courier.full_name != null
                    ? w.courier.full_name : "Livreur inconnu");
        }
        h.date.setText(longDateFr(w.requested_at));
        h.status.setText(statusLabel(w.status));
        h.status.setBackgroundColor(statusColor(w.status));

        // Only show these when the server actually stored a value.
        // The note carries the admin's reason for a rejection or a cancellation.
        if (w.note != null && !w.note.isEmpty()) {
            h.note.setText("Motif : " + w.note);
            h.note.setVisibility(View.VISIBLE);
        } else {
            h.note.setVisibility(View.GONE);
        }

        // Only a 'paid' row has one: it is the real bank/CCP transfer id.
        if (w.payout_reference != null && !w.payout_reference.isEmpty()) {
            h.ref.setText("Référence : " + w.payout_reference);
            h.ref.setVisibility(View.VISIBLE);
        } else {
            h.ref.setVisibility(View.GONE);
        }

        // Read-only screens never expose actions, whatever the status.
        boolean isPending = !readOnly && "pending".equals(w.status);
        boolean isApproved = !readOnly && "approved".equals(w.status);

        h.approve.setVisibility(isPending ? View.VISIBLE : View.GONE);
        h.reject.setVisibility(isPending ? View.VISIBLE : View.GONE);
        // An approved payout can either be settled or abandoned — both must be
        // reachable, otherwise a failed transfer leaves the money reserved forever.
        h.markPaid.setVisibility(isApproved ? View.VISIBLE : View.GONE);
        h.cancel.setVisibility(isApproved ? View.VISIBLE : View.GONE);
        // 'paid', 'rejected' and 'cancelled' are terminal: hide the whole bar.
        h.actions.setVisibility(isPending || isApproved ? View.VISIBLE : View.GONE);

        if (listener != null) {
            h.approve.setOnClickListener(v -> listener.onApprove(w));
            h.reject.setOnClickListener(v -> listener.onReject(w));
            h.markPaid.setOnClickListener(v -> listener.onMarkPaid(w));
            h.cancel.setOnClickListener(v -> listener.onCancel(w));
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    private String statusLabel(String status) {
        if (status == null) return "?";
        switch (status) {
            case "pending":   return "En attente";
            case "approved":  return "Approuvé";
            case "paid":      return "Payé";
            case "rejected":  return "Rejeté";
            case "cancelled": return "Annulé";
            default:          return status;
        }
    }

    private int statusColor(String status) {
        if (status == null) return 0xFF9E9E9E;
        switch (status) {
            case "pending":   return 0xFFF57C0D;
            case "approved":  return 0xFF1565C0;
            case "paid":      return 0xFF2E7D32;
            case "rejected":  return 0xFFC62828;
            case "cancelled": return 0xFF6D4C41;
            default:          return 0xFF9E9E9E;
        }
    }

    /**
     * "2026-08-16T19:01:06+00:00" -> "16 août 2026".
     *
     * <p>Only the first 10 characters are parsed: that avoids dealing with the
     * timezone suffix entirely, and the calendar day is all we display.
     *
     * <p>SimpleDateFormat rather than java.time because java.time needs API 26 while
     * this app's minSdk is 24 — the same mismatch that used to crash order creation.
     */
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
            return dayPart; // unexpected format: show the raw date rather than nothing
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView amount, status, courier, date, note, ref;
        Button approve, reject, markPaid, cancel;
        LinearLayout actions;

        ViewHolder(View v) {
            super(v);
            amount = v.findViewById(R.id.tvWAmount);
            status = v.findViewById(R.id.tvWStatus);
            courier = v.findViewById(R.id.tvWCourier);
            date = v.findViewById(R.id.tvWDate);
            note = v.findViewById(R.id.tvWNote);
            ref = v.findViewById(R.id.tvWRef);
            approve = v.findViewById(R.id.btnWApprove);
            reject = v.findViewById(R.id.btnWReject);
            markPaid = v.findViewById(R.id.btnWMarkPaid);
            cancel = v.findViewById(R.id.btnWCancel);
            actions = v.findViewById(R.id.llWActions);
        }
    }
}
