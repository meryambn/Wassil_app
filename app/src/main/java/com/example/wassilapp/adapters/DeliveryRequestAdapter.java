package com.example.wassilapp.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wassilapp.R;
import com.example.wassilapp.activities.NegotiatePriceActivity;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.OfferRepository;
import com.example.wassilapp.remote.dto.OfferDto;
import java.util.List;

public class DeliveryRequestAdapter extends RecyclerView.Adapter<DeliveryRequestAdapter.ViewHolder> {
    private List<Order> requests;
    private Context context;
    private int deliveryId;
    private String deliveryUid;
    private DatabaseHelper db;
    private final OfferRepository offerRepository = new OfferRepository();

    public DeliveryRequestAdapter(List<Order> requests, Context context, int deliveryId, String deliveryUid) {
        this.requests = requests;
        this.context = context;
        this.deliveryId = deliveryId;
        this.deliveryUid = deliveryUid;
        this.db = new DatabaseHelper(context);
    }

    public void updateRequests(List<Order> newRequests) {
        this.requests = newRequests;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_delivery_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order request = requests.get(position);
        User delivery = db.getUserById(deliveryId);

        holder.tvPackageType.setText(request.getPackageType());
        holder.tvWeight.setText(String.format("%.1f kg", request.getWeight()));
        holder.tvPickup.setText(request.getPickupAddress());
        holder.tvDrop.setText(request.getDropAddress());
        holder.tvPrice.setText(String.format("%.0f DA", request.getPrice()));

        holder.btnAccept.setOnClickListener(v -> {
            showNegotiationDialog(request, delivery);
        });

        holder.btnRefuse.setOnClickListener(v -> {
            requests.remove(position);
            notifyItemRemoved(position);
            Toast.makeText(context, "Demande refusée", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * The courier proposes a price; they do NOT take the order.
     *
     * <p>Per the cahier des charges the sender receives several propositions and
     * chooses ("Le livreur peut : Faire une offre"). Previously this called claim(),
     * which assigned the order to whoever tapped first, at whatever price they typed —
     * the sender had no say. The order now stays 'pending' and visible to other
     * couriers until the sender accepts one offer.
     */
    private void showNegotiationDialog(Order order, User delivery) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_negotiate, null);

        EditText etPrice = view.findViewById(R.id.etNegotiatedPrice);
        etPrice.setText(String.valueOf((int) order.getPrice()));

        builder.setView(view)
                .setTitle("Proposer un prix")
                .setPositiveButton("Envoyer l'offre", (dialog, which) -> {
                    String priceStr = etPrice.getText().toString();
                    if (priceStr.isEmpty()) {
                        Toast.makeText(context, "Entrez un prix", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double offeredPrice;
                    try {
                        offeredPrice = Double.parseDouble(priceStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Prix invalide", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (deliveryUid == null) {
                        Toast.makeText(context,
                                "Reconnectez-vous pour envoyer une offre", Toast.LENGTH_LONG).show();
                        return;
                    }

                    offerRepository.makeOffer(order.getOrderId(), deliveryUid, offeredPrice, null,
                            new OfferRepository.OfferCallback() {
                                @Override
                                public void onSuccess(OfferDto offer) {
                                    Toast.makeText(context,
                                            "Offre envoyée: " + (int) offeredPrice
                                                    + " DA — en attente de la réponse de l'expéditeur",
                                            Toast.LENGTH_LONG).show();
                                    removeFromList(order);
                                }

                                @Override
                                public void onError(String message) {
                                    // A duplicate offer hits the (order_id, courier_id) unique
                                    // constraint, which is the common case here.
                                    String friendly = message != null && message.contains("duplicate")
                                            ? "Vous avez déjà fait une offre sur cette commande"
                                            : "Envoi impossible: " + message;
                                    Toast.makeText(context, friendly, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void removeFromList(Order order) {
        requests.remove(order);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return requests != null ? requests.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPackageType, tvWeight, tvPickup, tvDrop, tvPrice;
        Button btnAccept, btnRefuse;

        ViewHolder(View itemView) {
            super(itemView);
            tvPackageType = itemView.findViewById(R.id.tvPackageType);
            tvWeight = itemView.findViewById(R.id.tvWeight);
            tvPickup = itemView.findViewById(R.id.tvPickupAddress);
            tvDrop = itemView.findViewById(R.id.tvDropAddress);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnRefuse = itemView.findViewById(R.id.btnRefuse);
        }
    }
}