package com.example.wassilapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.models.AiMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for AI Assistant chat messages and structured action cards.
 */
public class AiMessageAdapter extends RecyclerView.Adapter<AiMessageAdapter.MessageViewHolder> {

    public interface OnActionClickListener {
        void onActionCardClicked(AiMessage message);
    }

    private final List<AiMessage> messages;
    private final OnActionClickListener actionClickListener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public AiMessageAdapter(List<AiMessage> messages, OnActionClickListener actionClickListener) {
        this.messages = messages;
        this.actionClickListener = actionClickListener;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        AiMessage message = messages.get(position);
        String timeStr = timeFormat.format(new Date(message.getTimestamp()));

        if (message.isUser()) {
            holder.layoutUserContainer.setVisibility(View.VISIBLE);
            holder.layoutAiContainer.setVisibility(View.GONE);
            holder.tvUserText.setText(message.getText());
            holder.tvUserTime.setText(timeStr);
        } else {
            holder.layoutUserContainer.setVisibility(View.GONE);
            holder.layoutAiContainer.setVisibility(View.VISIBLE);
            holder.tvAiText.setText(message.getText());
            holder.tvAiTime.setText(timeStr);

            if (message.hasActionCard()) {
                holder.cardAction.setVisibility(View.VISIBLE);
                holder.tvCardTitle.setText(message.getCardTitle());
                holder.tvCardSubtitle.setText(message.getCardSubtitle());

                if (message.getCardBadge() != null && !message.getCardBadge().isEmpty()) {
                    holder.tvCardBadge.setVisibility(View.VISIBLE);
                    holder.tvCardBadge.setText(message.getCardBadge());
                } else {
                    holder.tvCardBadge.setVisibility(View.GONE);
                }

                if (message.getCardButtonText() != null && !message.getCardButtonText().isEmpty()) {
                    holder.btnCardAction.setText(message.getCardButtonText());
                }

                holder.btnCardAction.setOnClickListener(v -> {
                    if (actionClickListener != null) {
                        actionClickListener.onActionCardClicked(message);
                    }
                });
            } else {
                holder.cardAction.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout layoutUserContainer;
        final TextView tvUserText;
        final TextView tvUserTime;

        final LinearLayout layoutAiContainer;
        final TextView tvAiText;
        final TextView tvAiTime;

        final CardView cardAction;
        final TextView tvCardTitle;
        final TextView tvCardSubtitle;
        final TextView tvCardBadge;
        final Button btnCardAction;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutUserContainer = itemView.findViewById(R.id.layoutUserContainer);
            tvUserText = itemView.findViewById(R.id.tvUserText);
            tvUserTime = itemView.findViewById(R.id.tvUserTime);

            layoutAiContainer = itemView.findViewById(R.id.layoutAiContainer);
            tvAiText = itemView.findViewById(R.id.tvAiText);
            tvAiTime = itemView.findViewById(R.id.tvAiTime);

            cardAction = itemView.findViewById(R.id.cardAction);
            tvCardTitle = itemView.findViewById(R.id.tvCardTitle);
            tvCardSubtitle = itemView.findViewById(R.id.tvCardSubtitle);
            tvCardBadge = itemView.findViewById(R.id.tvCardBadge);
            btnCardAction = itemView.findViewById(R.id.btnCardAction);
        }
    }
}
