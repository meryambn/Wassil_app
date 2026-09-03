package com.example.wassilapp.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AI-calibrated Intelligent Delivery Estimator for WASSIL platform.
 *
 * <p>Implements multi-criteria dynamic pricing, volumetric parcel weight calculation,
 * vehicle recommendation engine, and ETA estimation calibrated against Algerian
 * market benchmark tariffs (Yalidine, Maystro, ZR Express, Noest, EcoTrack).
 */
public class PriceEstimator {

    // Calibrated market tariff benchmarks by zone (in DZD)
    private static final double BASE_URBAN_ALGIERS = 300.0;
    private static final double BASE_NORTH_REGIONAL = 450.0;
    private static final double BASE_HIGHLANDS_REGIONAL = 550.0;
    private static final double BASE_SOUTH_REGIONAL = 850.0;
    private static final double BASE_DEEP_SOUTH_REGIONAL = 1250.0;

    private static final double RATE_PER_KM_URBAN = 35.0; // DA/km
    private static final double RATE_PER_KM_INTER = 25.0; // DA/km
    private static final double RATE_PER_EXTRA_KG = 20.0; // DA/kg above 2kg

    // Regional Wilaya Zone Mapping
    private static final Map<String, Integer> WILAYA_ZONES = new HashMap<>();

    static {
        // Zone 0: North / Coastal
        String[] north = {"alger", "blida", "boumerdes", "tipaza", "oran", "constantine",
                "annaba", "setif", "tlemcen", "bejaia", "chlef", "jijel", "skikda",
                "mostaganem", "mascara", "relizane", "ain defla", "ain temouchent",
                "tizi ouzou", "bouira", "medea", "tarf", "el tarf", "tissemsilt", "mila", "guelma"};
        for (String w : north) WILAYA_ZONES.put(w, 0);

        // Zone 1: Highlands / Hauts-Plateaux
        String[] highlands = {"batna", "djelfa", "tiaret", "khenchela", "souk ahras",
                "bordj bou arreridj", "msila", "laghouat", "oum el bouaghi", "tebessa", "saida", "el bayadh", "naama"};
        for (String w : highlands) WILAYA_ZONES.put(w, 1);

        // Zone 2: Near South
        String[] south = {"biskra", "ghardaia", "ouargla", "el oued", "touggourt", "el m'ghair", "el menia", "ouled djellal"};
        for (String w : south) WILAYA_ZONES.put(w, 2);

        // Zone 3: Deep / Extreme South
        String[] deepSouth = {"bechar", "adrar", "tamanrasset", "illizi", "tindouf", "djanet",
                "in salah", "in guezzam", "timimoun", "beni abbes", "bordj badji mokhtar"};
        for (String w : deepSouth) WILAYA_ZONES.put(w, 3);
    }

    /** Estimation parameters input container. */
    public static class EstimateParams {
        public double distanceKm;
        public double weightKg;
        public Double lengthCm;
        public Double widthCm;
        public Double heightCm;
        public String packageType = "Colis";
        public String pickupWilaya;
        public String dropWilaya;
        public boolean isUrgent;
        public String selectedVehicle;

        public EstimateParams(double distanceKm, double weightKg, String packageType) {
            this.distanceKm = distanceKm;
            this.weightKg = weightKg;
            this.packageType = packageType;
        }
    }

    /** Detailed estimation result breakdown. */
    public static class EstimateResult {
        public double estimatedPrice;
        public double minPrice;
        public double maxPrice;
        public int estimatedMinutes;
        public String recommendedVehicle;
        public String vehicleReason;
        public double volumetricWeightKg;
        public double billableWeightKg;
        public double volumeM3;
        public boolean isInterWilaya;
        public String breakdownSummary;
    }

    /**
     * Parses dimension string in format 'LxWxH' or 'L*W*H' (e.g. "30x20x15" or "40*30*20").
     * @return array of [length, width, height] in cm, or null if unparseable.
     */
    public static double[] parseDimensions(String dimStr) {
        if (dimStr == null || dimStr.trim().isEmpty()) {
            return null;
        }
        String cleaned = dimStr.toLowerCase(Locale.ROOT).replace(" ", "").replace("*", "x");
        String[] parts = cleaned.split("x");
        if (parts.length == 3) {
            try {
                double l = Double.parseDouble(parts[0]);
                double w = Double.parseDouble(parts[1]);
                double h = Double.parseDouble(parts[2]);
                if (l > 0 && w > 0 && h > 0) {
                    return new double[]{l, w, h};
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * Multi-criteria AI estimation engine.
     */
    public static EstimateResult estimateDetailed(EstimateParams params) {
        EstimateResult res = new EstimateResult();

        // 1. Calculate volumetric weight & volume: (L * W * H) / 5000
        double volWeight = 0.0;
        double volumeM3 = 0.0;
        if (params.lengthCm != null && params.widthCm != null && params.heightCm != null
                && params.lengthCm > 0 && params.widthCm > 0 && params.heightCm > 0) {
            volWeight = (params.lengthCm * params.widthCm * params.heightCm) / 5000.0;
            volumeM3 = (params.lengthCm * params.widthCm * params.heightCm) / 1000000.0;
        }
        res.volumetricWeightKg = Math.round(volWeight * 10.0) / 10.0;
        res.volumeM3 = Math.round(volumeM3 * 1000.0) / 1000.0;

        double actualWeight = Math.max(params.weightKg, 0.5);
        res.billableWeightKg = Math.max(actualWeight, res.volumetricWeightKg);

        // 2. Zone Analysis
        String pWilaya = normalizeWilaya(params.pickupWilaya);
        String dWilaya = normalizeWilaya(params.dropWilaya);
        res.isInterWilaya = (pWilaya != null && dWilaya != null && !pWilaya.equals(dWilaya));

        int dropZone = getZone(dWilaya);
        double baseFare;
        double kmRate;

        if (!res.isInterWilaya) {
            baseFare = BASE_URBAN_ALGIERS;
            kmRate = RATE_PER_KM_URBAN;
        } else {
            kmRate = RATE_PER_KM_INTER;
            switch (dropZone) {
                case 1: baseFare = BASE_HIGHLANDS_REGIONAL; break;
                case 2: baseFare = BASE_SOUTH_REGIONAL; break;
                case 3: baseFare = BASE_DEEP_SOUTH_REGIONAL; break;
                default: baseFare = BASE_NORTH_REGIONAL; break;
            }
        }

        // 3. Vehicle Recommendation Engine
        if (res.billableWeightKg > 50 || res.volumeM3 > 0.35) {
            res.recommendedVehicle = "Camion";
            res.vehicleReason = "Colis lourd ou volumineux (> 50kg ou > 0.35m³)";
        } else if (res.billableWeightKg > 10 || res.volumeM3 > 0.04 || "Fragile".equalsIgnoreCase(params.packageType)) {
            res.recommendedVehicle = "Voiture";
            res.vehicleReason = "Volume moyen ou marchandise fragile (10-50kg)";
        } else {
            res.recommendedVehicle = "Moto";
            res.vehicleReason = "Idéal pour petits colis et plis (< 10kg)";
        }

        // 4. Package Type Multipliers
        double typeMultiplier = 1.0;
        String type = params.packageType != null ? params.packageType : "Colis";
        switch (type) {
            case "Électronique":
            case "Electronique":
                typeMultiplier = 1.20;
                break;
            case "Fragile":
                typeMultiplier = 1.25;
                break;
            case "Document":
            case "Documents":
                typeMultiplier = 0.85;
                break;
            case "Alimentaire":
                typeMultiplier = 1.10;
                break;
            default:
                typeMultiplier = 1.00;
                break;
        }

        // 5. Urgency Multiplier
        double urgencyMultiplier = params.isUrgent ? 1.35 : 1.0;

        // 6. Calculate Price
        double effectiveDist = Math.max(params.distanceKm, 1.0);
        double extraWeight = Math.max(res.billableWeightKg - 2.0, 0.0);

        double rawPrice = (baseFare + (effectiveDist * kmRate) + (extraWeight * RATE_PER_EXTRA_KG))
                * typeMultiplier * urgencyMultiplier;

        // Round to nearest 10 DZD
        res.estimatedPrice = Math.max(Math.round(rawPrice / 10.0) * 10.0, 200.0);
        res.minPrice = Math.max(res.estimatedPrice - 50.0, 200.0);
        res.maxPrice = res.estimatedPrice + 100.0;

        // 7. Calculate ETA
        String activeVehicle = params.selectedVehicle != null ? params.selectedVehicle : res.recommendedVehicle;
        double speed = "Moto".equalsIgnoreCase(activeVehicle) ? 25.0 : ("Voiture".equalsIgnoreCase(activeVehicle) ? 20.0 : 15.0);
        int buffer = "Moto".equalsIgnoreCase(activeVehicle) ? 10 : 20;
        int rawMinutes = (int) Math.ceil((effectiveDist / speed) * 60.0) + buffer;

        if (params.isUrgent) {
            rawMinutes = Math.max((int) (rawMinutes * 0.75), 15);
        }
        res.estimatedMinutes = rawMinutes;

        res.breakdownSummary = String.format(Locale.getDefault(),
                "Base: %.0f DA | Dist (%.1f km): %.0f DA | Poids (%.1f kg): %.0f DA",
                baseFare, effectiveDist, effectiveDist * kmRate, res.billableWeightKg, extraWeight * RATE_PER_EXTRA_KG);

        return res;
    }

    /** Legacy backward-compatible estimator. */
    public static double estimatePrice(double distanceKm, double weightKg, String packageType) {
        EstimateParams params = new EstimateParams(distanceKm, weightKg, packageType);
        return estimateDetailed(params).estimatedPrice;
    }

    /** Legacy backward-compatible ETA estimator. */
    public static int estimateTime(double distanceKm) {
        return (int) Math.ceil((Math.max(distanceKm, 1.0) / 20.0) * 60) + 15;
    }

    private static String normalizeWilaya(String w) {
        if (w == null) return null;
        return w.trim().toLowerCase(Locale.ROOT).replace("é", "e").replace("è", "e").replace("'", "").replace("-", " ");
    }

    private static int getZone(String wilaya) {
        if (wilaya == null) return 0;
        Integer z = WILAYA_ZONES.get(wilaya);
        return z != null ? z : 0;
    }
}
