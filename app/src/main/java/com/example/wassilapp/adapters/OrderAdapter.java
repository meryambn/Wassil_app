package com.example.wassilapp.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wassilapp.R;
import com.example.wassilapp.activities.OrderTrackingActivity;
import com.example.wassilapp.models.Order;
import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {
    private List<Order> orders;
    private Context context;
    private String userType;
    private OnOrderClickListener clickListener;

    /** Lets a screen override what tapping an order does (e.g. show offers instead). */
    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    public OrderAdapter(List<Order> orders, Context context, String userType) {
        this.orders = orders;
        this.context = context;
        this.userType = userType;
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);

        holder.tvOrderId.setText(order.getOrderId());
        holder.tvStatus.setText(getStatusFrench(order.getStatus()));
        holder.tvPrice.setText(String.format("%.0f DA", order.getNegotiatedPrice()));
        holder.tvPickup.setText(order.getPickupAddress());
        holder.tvDrop.setText(order.getDropAddress());

        // Status color
        int color = getStatusColor(order.getStatus());
        holder.tvStatus.setTextColor(context.getColor(color));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onOrderClick(order);
                return;
            }
            Intent intent = new Intent(context, OrderTrackingActivity.class);
            intent.putExtra("order_id", order.getOrderId());
            intent.putExtra("user_type", userType);
            context.startActivity(intent);
        });
    }

    private String getStatusFrench(String status) {
        switch (status) {
            // Cahier des charges p.3 wording, kept identical to OrderTrackingActivity.
            case "pending": return "Recherche d'un livreur";
            case "prise_en_charge": return "Livreur accepté";
            case "vers_depart": return "Vers le point de départ";
            case "colis_recupere": return "Colis récupéré";
            case "en_route": return "En cours de livraison";
            case "livre": return "Livré";
            case "annule": return "Annulé";
            default: return status;
        }
    }

    private int getStatusColor(String status) {
        switch (status) {
            case "en_route": return R.color.status_en_route;
            case "livre": return R.color.status_livre;
            case "prise_en_charge":
            case "vers_depart":
            case "colis_recupere": return R.color.status_prise_en_charge;
            default: return R.color.text_secondary;
        }
    }

    @Override
    public int getItemCount() {
        return orders != null ? orders.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvStatus, tvPrice, tvPickup, tvDrop;

        ViewHolder(View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvPrice = itemView.findViewById(R.id.tvOrderPrice);
            tvPickup = itemView.findViewById(R.id.tvPickup);
            tvDrop = itemView.findViewById(R.id.tvDrop);
        }
    }
}