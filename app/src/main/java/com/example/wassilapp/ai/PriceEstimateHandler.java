package com.example.wassilapp.ai;

import com.example.wassilapp.models.AiMessage;
import com.example.wassilapp.utils.PriceEstimator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handles price estimation requests using {@link PriceEstimator} as the sole source of truth.
 * The LLM or assistant does not calculate or invent prices.
 */
public class PriceEstimateHandler {

    // Approximate inter-wilaya driving road distances (km) from Algiers baseline
    private static final Map<String, Double> DISTANCE_FROM_ALGIERS = new HashMap<>();

    static {
        DISTANCE_FROM_ALGIERS.put("alger", 12.0);
        DISTANCE_FROM_ALGIERS.put("blida", 45.0);
        DISTANCE_FROM_ALGIERS.put("boumerdes", 50.0);
        DISTANCE_FROM_ALGIERS.put("tipaza", 70.0);
        DISTANCE_FROM_ALGIERS.put("medea", 85.0);
        DISTANCE_FROM_ALGIERS.put("tizi ouzou", 100.0);
        DISTANCE_FROM_ALGIERS.put("bouira", 120.0);
        DISTANCE_FROM_ALGIERS.put("chlef", 200.0);
        DISTANCE_FROM_ALGIERS.put("setif", 300.0);
        DISTANCE_FROM_ALGIERS.put("bejaia", 240.0);
        DISTANCE_FROM_ALGIERS.put("bordj bou arreridj", 230.0);
        DISTANCE_FROM_ALGIERS.put("oran", 420.0);
        DISTANCE_FROM_ALGIERS.put("constantine", 390.0);
        DISTANCE_FROM_ALGIERS.put("batna", 430.0);
        DISTANCE_FROM_ALGIERS.put("annaba", 550.0);
        DISTANCE_FROM_ALGIERS.put("tlemcen", 520.0);
        DISTANCE_FROM_ALGIERS.put("biskra", 400.0);
        DISTANCE_FROM_ALGIERS.put("djelfa", 290.0);
        DISTANCE_FROM_ALGIERS.put("ouargla", 780.0);
        DISTANCE_FROM_ALGIERS.put("ghardaia", 600.0);
        DISTANCE_FROM_ALGIERS.put("bechar", 950.0);
        DISTANCE_FROM_ALGIERS.put("tamanrasset", 1900.0);
    }

    public static AiMessage handlePriceEstimation(ConversationState state) {
        state.setActiveIntent(AiIntent.PRICE_ESTIMATE);

        String origin = state.getOriginWilaya();
        String destination = state.getDestinationWilaya();
        Double weight = state.getWeightKg();

        // 1. Check for missing slots and ask specifically without repetition

        if (origin == null && destination == null) {
            return new AiMessage(
                    "Pour calculer votre tarif de livraison, de quelle Wilaya vers quelle Wilaya souhaitez-vous envoyer votre colis ?",
                    false
            );
        }

        if (origin == null) {
            return new AiMessage(
                    "D'accord, vers " + destination + ". Mais de quelle Wilaya partez-vous ?",
                    false
            );
        }

        if (destination == null) {
            return new AiMessage(
                    "Très bien, depuis " + origin + ". Vers quelle Wilaya de destination souhaitez-vous livrer ?",
                    false
            );
        }

        if (weight == null || weight <= 0) {
            return new AiMessage(
                    "Parfait pour le trajet " + origin + " → " + destination + ". Quel est le poids approximatif de votre colis (en kg) ?",
                    false
            );
        }

        // 2. All required parameters present -> Execute PriceEstimator
        double distanceKm = estimateRoadDistance(origin, destination);

        PriceEstimator.EstimateParams params = new PriceEstimator.EstimateParams(
                distanceKm, weight, state.getPackageType()
        );
        params.pickupWilaya = origin;
        params.dropWilaya = destination;
        params.isUrgent = state.isUrgent();
        params.lengthCm = state.getLengthCm();
        params.widthCm = state.getWidthCm();
        params.heightCm = state.getHeightCm();

        PriceEstimator.EstimateResult result = PriceEstimator.estimateDetailed(params);

        // 3. Build Natural-Language Explanation
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.FRENCH,
                "Voici l'estimation calculée pour votre trajet **%s → %s** :\n\n",
                origin, destination));
        sb.append(String.format(Locale.FRENCH,
                "💰 **Tarif conseillé : %.0f DA** (Fourchette : %.0f - %.0f DA)\n",
                result.estimatedPrice, result.minPrice, result.maxPrice));
        sb.append(String.format(Locale.FRENCH,
                "🚗 **Véhicule recommandé : %s** (%s)\n",
                result.recommendedVehicle, result.vehicleReason));
        sb.append(String.format(Locale.FRENCH,
                "⏱️ **Durée estimée : ~%d min** (Distance : %.0f km)\n",
                result.estimatedMinutes, distanceKm));

        if (result.volumetricWeightKg > weight) {
            sb.append(String.format(Locale.FRENCH,
                    "📦 *Poids volumétrique appliqué : %.1f kg (Volume : %.3f m³)*\n",
                    result.volumetricWeightKg, result.volumeM3));
        }

        if (state.isUrgent()) {
            sb.append("⚡ *Option Express prioritaire incluse (+35%)*\n");
        }

        sb.append("\nVous pouvez créer cette livraison directement avec les paramètres préremplis ci-dessous :");

        AiMessage response = new AiMessage(sb.toString(), false);

        // 4. Structured Action Card
        response.setHasActionCard(true);
        response.setActionType(AiMessage.ACTION_CREATE_DELIVERY);
        response.setCardTitle("Livraison " + origin + " → " + destination);
        response.setCardSubtitle(result.recommendedVehicle + " • ~" + result.estimatedMinutes + " min");
        response.setCardBadge(String.format(Locale.getDefault(), "%.0f DA", result.estimatedPrice));
        response.setCardButtonText("Créer cette livraison");

        response.setPrefillPickup(origin);
        response.setPrefillDrop(destination);
        response.setPrefillWeight(weight);
        response.setPrefillPackageType(state.getPackageType());
        response.setPrefillUrgent(state.isUrgent());
        if (state.getLengthCm() != null && state.getWidthCm() != null && state.getHeightCm() != null) {
            response.setPrefillDimensions(String.format(Locale.ROOT, "%.0fx%.0fx%.0f",
                    state.getLengthCm(), state.getWidthCm(), state.getHeightCm()));
        }

        // Clear slots now that estimate was provided
        state.clearPriceSlots();

        return response;
    }

    private static double estimateRoadDistance(String origin, String dest) {
        String o = EntityExtractor.normalize(origin);
        String d = EntityExtractor.normalize(dest);

        if (o.equals(d)) {
            // Intra-wilaya delivery
            return 12.0;
        }

        Double d1 = DISTANCE_FROM_ALGIERS.get(o);
        Double d2 = DISTANCE_FROM_ALGIERS.get(d);

        if (o.equals("alger") && d2 != null) return d2;
        if (d.equals("alger") && d1 != null) return d1;

        if (d1 != null && d2 != null) {
            // Triangular highway estimation between wilayas
            return Math.max(Math.abs(d1 - d2) * 1.1, 40.0);
        }

        // Default inter-wilaya regional estimate
        return 120.0;
    }
}
