package com.example.wassilapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.remote.dto.MessageDto;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Renders a conversation as chat bubbles.
 *
 * <p>Two row layouts share one ViewHolder because they expose the same view ids —
 * getItemViewType picks which one to inflate. The choice is made by asking "was this
 * written by me?", i.e. comparing the message's sender_id to the signed-in user's
 * uid. It is deliberately NOT "is this the order's sender", because the courier is
 * also a sender of messages; that test would put the courier's own words on the
 * wrong side of their screen.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<MessageDto> messages;
    private final Context context;
    private final String myUid;

    public MessageAdapter(List<MessageDto> messages, Context context, String myUid) {
        this.messages = messages;
        this.context = context;
        this.myUid = myUid;
    }

    @Override
    public int getItemViewType(int position) {
        MessageDto m = messages.get(position);
        return myUid != null && myUid.equals(m.sender_id) ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;
        return new ViewHolder(LayoutInflater.from(context).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MessageDto m = messages.get(position);
        holder.tvBody.setText(m.body != null ? m.body : "");
        holder.tvTime.setText(timeOfDay(m.created_at));
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    /**
     * "2026-08-23T16:11:42.123456+00:00" -> "17:11" on a phone set to Algiers.
     *
     * <p>Two things matter here. First, the fractional seconds vary in length because
     * Postgres trims trailing zeros, so a fixed pattern like SSSSSS would throw on
     * some rows — only the first 19 characters are parsed, which is exactly through
     * the seconds. Second, dropping the suffix also drops the "+00:00", so the parser
     * must be told the value is UTC. Without that line the phone would read 16:11 UTC
     * as 16:11 local and every message in Algeria (UTC+1) would display an hour early
     * — a bug that looks like nothing at all until you compare it to the clock.
     *
     * <p>SimpleDateFormat rather than java.time because java.time needs API 26 while
     * this app's minSdk is 24.
     */
    private String timeOfDay(String iso) {
        if (iso == null || iso.length() < 19) {
            return "";
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = parser.parse(iso.substring(0, 19));
            if (parsed == null) {
                return "";
            }
            // No explicit timezone here: the default is the device's, which is the
            // one the person reading the screen actually lives in.
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed);
        } catch (ParseException e) {
            return ""; // unexpected format: show the bubble without a timestamp
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBody, tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            tvBody = itemView.findViewById(R.id.tvMessageBody);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }
}
