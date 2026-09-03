package com.example.wassilapp.ai;

import android.content.Context;

import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.AiMessage;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.utils.SessionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Handles order tracking queries.
 * The database is the strict source of truth: no simulated or hallucinated orders.
 */
public class OrderTrackingHandler {

    private static final List<String> ACTIVE_STATUSES = Arrays.asList(
            "pending", "prise_en_charge", "vers_depart", "colis_recupere", "en_route"
    );

    public static AiMessage handleTracking(Context context, ConversationState state) {
        DatabaseHelper db = new DatabaseHelper(context);
        SessionManager session = new SessionManager(context);

        int userId = session.getUserId();
        String role = session.getUserRole();

        // Query user orders from local database (which syncs with Supabase)
        List<Order> userOrders = "delivery".equalsIgnoreCase(role)
                ? db.getOrdersByDelivery(userId)
                : db.getOrdersBySender(userId);

        if (userOrders == null) {
            userOrders = new ArrayList<>();
        }

        // 1. If user specified an explicit Order ID (e.g., "où est ma commande #3 ?")
        String queryOrderId = state.getTargetOrderId();
        Order targetedOrder = null;

        if (queryOrderId != null && !queryOrderId.isEmpty()) {
            for (Order o : userOrders) {
                if (queryOrderId.equalsIgnoreCase(o.getOrderId())) {
                    targetedOrder = o;
                    break;
                }
            }
            if (targetedOrder == null) {
                // Try global db lookup with ownership check
                Order direct = db.getOrderById(queryOrderId);
                if (direct != null && (direct.getSenderId() == userId || direct.getDeliveryId() == userId)) {
                    targetedOrder = direct;
                }
            }
        }

        // 2. If no specific order ID or not found, pick the most recent active order
        if (targetedOrder == null) {
            for (Order o : userOrders) {
                if (ACTIVE_STATUSES.contains(o.getStatus())) {
                    targetedOrder = o;
                    break; // userOrders is sorted by created_at desc
                }
            }
        }

        // 3. Handle Empty Database / No Active Orders
        if (targetedOrder == null) {
            if (userOrders.isEmpty()) {
                AiMessage emptyMsg = new AiMessage(
                        "Vous n'avez actuellement aucune commande enregistrée sur votre compte.\n\n" +
                                "Vous pouvez créer une nouvelle livraison ou demander une estimation de tarif à tout moment !",
                        false
                );
                emptyMsg.setHasActionCard(true);
                emptyMsg.setActionType(AiMessage.ACTION_CREATE_DELIVERY);
                emptyMsg.setCardTitle("Nouvelle livraison");
                emptyMsg.setCardSubtitle("Commandez un coursier rapidement");
                emptyMsg.setCardBadge("WASSIL");
                emptyMsg.setCardButtonText("Créer une livraison");
                return emptyMsg;
            } else {
                // User has orders, but all are already delivered or cancelled
                Order lastCompleted = userOrders.get(0);
                String statFr = translateStatus(lastCompleted.getStatus());
                return new AiMessage(
                        String.format(Locale.FRENCH,
                                "Vous n'avez aucune livraison **en cours** pour le moment.\n" +
                                        "Votre dernière commande (#%s) est marquée comme : **%s**.",
                                lastCompleted.getOrderId(), statFr),
                        false
                );
            }
        }

        // 4. Found Active Order -> Build Tracking Response
        String frenchStatus = translateStatus(targetedOrder.getStatus());
        String courierInfo = "";

        if (targetedOrder.getDeliveryId() > 0) {
            User courier = db.getUserById(targetedOrder.getDeliveryId());
            if (courier != null && courier.getFullName() != null) {
                courierInfo = "\n👤 **Livreur assigné :** " + courier.getFullName() +
                        (courier.getVehicleType() != null && !courier.getVehicleType().isEmpty() ? " (" + courier.getVehicleType() + ")" : "");
            }
        } else {
            courierInfo = "\n⏳ **Livreur :** En attente d'acceptation par un coursier disponible.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.FRENCH,
                "Voici les informations de suivi pour la commande **#%s** :\n\n",
                targetedOrder.getOrderId()));
        sb.append("📍 **Statut actuel :** ").append(frenchStatus).append("\n");
        sb.append("📦 **Départ :** ").append(targetedOrder.getPickupAddress()).append("\n");
        sb.append("🎯 **Destination :** ").append(targetedOrder.getDropAddress()).append("\n");
        sb.append(String.format(Locale.FRENCH, "💵 **Prix convenu :** %.0f DA\n", targetedOrder.getNegotiatedPrice() > 0 ? targetedOrder.getNegotiatedPrice() : targetedOrder.getPrice()));
        sb.append(courierInfo).append("\n\n");
        sb.append("Vous pouvez suivre l'itinéraire et la position en direct sur la carte :");

        AiMessage response = new AiMessage(sb.toString(), false);
        response.setHasActionCard(true);
        response.setActionType(AiMessage.ACTION_TRACK_ORDER);
        response.setCardTitle("Commande #" + targetedOrder.getOrderId());
        response.setCardSubtitle(targetedOrder.getPickupAddress() + " → " + targetedOrder.getDropAddress());
        response.setCardBadge(frenchStatus);
        response.setCardButtonText("Suivre sur la carte");
        response.setActionOrderId(targetedOrder.getOrderId());

        state.setTargetOrderId(null);
        return response;
    }

    public static AiMessage handleListOrders(Context context) {
        DatabaseHelper db = new DatabaseHelper(context);
        SessionManager session = new SessionManager(context);
        int userId = session.getUserId();
        String role = session.getUserRole();

        List<Order> orders = "delivery".equalsIgnoreCase(role) ?
                db.getOrdersByDelivery(userId) : db.getOrdersBySender(userId);

        if (orders == null || orders.isEmpty()) {
            return new AiMessage(
                    "Vous n'avez aucune commande dans votre historique pour l'instant.",
                    false
            );
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.FRENCH, "📋 Vous avez **%d commande(s)** au total :\n\n", orders.size()));

        int count = Math.min(orders.size(), 5);
        for (int i = 0; i < count; i++) {
            Order o = orders.get(i);
            String stat = translateStatus(o.getStatus());
            sb.append(String.format(Locale.FRENCH,
                    "• **#%s** : %s → %s (**%s**, %.0f DA)\n",
                    o.getOrderId(), o.getPickupAddress(), o.getDropAddress(), stat, o.getPrice()));
        }

        if (orders.size() > 5) {
            sb.append(String.format(Locale.FRENCH, "\n*... et %d autres commandes dans l'historique.*", orders.size() - 5));
        }

        return new AiMessage(sb.toString(), false);
    }

    public static String translateStatus(String status) {
        if (status == null) return "Inconnu";
        switch (status.toLowerCase(Locale.ROOT)) {
            case "pending":
                return "En attente";
            case "prise_en_charge":
                return "Pris en charge";
            case "vers_depart":
                return "Livreur vers départ";
            case "colis_recupere":
                return "Colis récupéré";
            case "en_route":
                return "En route";
            case "livre":
                return "Livré";
            case "annule":
                return "Annulé";
            default:
                return status;
        }
    }
}
