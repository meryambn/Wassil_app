package com.example.wassilapp.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based Entity Extractor for the WASSIL platform.
 * Normalizes accents, capitalization, and extracts Wilayas, weights, package types,
 * dimensions, and urgency parameters.
 */
public class EntityExtractor {

    // Standard list of Algerian Wilayas (ordered with longest names first to prevent partial match)
    private static final List<String> WILAYAS = Arrays.asList(
            "Bordj Bou Arreridj", "Bordj Badji Mokhtar", "Sidi Bel Abbes", "Ain Temouchent",
            "Oum El Bouaghi", "El M'Ghair", "El Menia", "Ouled Djellal", "Beni Abbes",
            "In Salah", "In Guezzam", "Tizi Ouzou", "Ain Defla", "Souk Ahras",
            "El Tarf", "El Oued", "Tamanrasset", "Constantine", "Mostaganem",
            "Boumerdes", "Tissemsilt", "Khenchela", "Ghardaia", "Relizane",
            "Timimoun", "Touggourt", "Mascara", "Ouargla", "El Bayadh", "Tipaza",
            "Tlemcen", "Bechar", "Laghouat", "Tebessa", "Skikda", "Guelma",
            "Chlef", "Batna", "Bejaia", "Biskra", "Blida", "Bouira", "Tiaret",
            "Djelfa", "Jijel", "Setif", "Saida", "Annaba", "Medea", "M'Sila",
            "Illizi", "Tindouf", "Adrar", "Djanet", "Oran", "Alger", "Mila"
    );

    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(?:kg|kilo|kilos|kilogrammes|kilogramme)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern GRAM_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(?:g|grammes|gramme)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIMENSIONS_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*[xX*]\\s*(\\d+(?:[.,]\\d+)?)\\s*[xX*]\\s*(\\d+(?:[.,]\\d+)?)");

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
            "(?:commande|colis|order|livraison)\\s*(?:#|n[°o]|num[ée]ro)?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ISOLATED_NUMBER_PATTERN = Pattern.compile(
            "^\\s*#?\\s*(\\d+(?:[.,]\\d+)?)\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Normalizes text by lowercasing, stripping diacritics/accents, and trimming extra spaces.
     */
    public static String normalize(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("'", " ")
                .replace("’", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    /**
     * Extracts and populates slots inside {@link ConversationState} from user input.
     */
    public static void extractEntities(String rawInput, ConversationState state) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return;
        }

        String norm = normalize(rawInput);

        // 1. Extract Weight
        Matcher weightMatcher = WEIGHT_PATTERN.matcher(norm);
        if (weightMatcher.find()) {
            try {
                double w = Double.parseDouble(weightMatcher.group(1).replace(",", "."));
                state.setWeightKg(w);
            } catch (NumberFormatException ignored) {}
        } else {
            Matcher gramMatcher = GRAM_PATTERN.matcher(norm);
            if (gramMatcher.find()) {
                try {
                    double g = Double.parseDouble(gramMatcher.group(1).replace(",", "."));
                    state.setWeightKg(Math.max(g / 1000.0, 0.1));
                } catch (NumberFormatException ignored) {}
            } else if (state.getActiveIntent() == AiIntent.PRICE_ESTIMATE && state.getWeightKg() == null) {
                // If user was prompted for weight and simply replied "5" or "10"
                Matcher numMatcher = ISOLATED_NUMBER_PATTERN.matcher(norm);
                if (numMatcher.find()) {
                    try {
                        double val = Double.parseDouble(numMatcher.group(1).replace(",", "."));
                        if (val > 0 && val <= 500) {
                            state.setWeightKg(val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 2. Extract Dimensions
        Matcher dimMatcher = DIMENSIONS_PATTERN.matcher(norm);
        if (dimMatcher.find()) {
            try {
                double l = Double.parseDouble(dimMatcher.group(1).replace(",", "."));
                double w = Double.parseDouble(dimMatcher.group(2).replace(",", "."));
                double h = Double.parseDouble(dimMatcher.group(3).replace(",", "."));
                state.setLengthCm(l);
                state.setWidthCm(w);
                state.setHeightCm(h);
            } catch (NumberFormatException ignored) {}
        }

        // 3. Extract Wilayas (Origin and Destination)
        extractWilayas(norm, state);

        // 4. Extract Package Type
        if (norm.contains("fragile") || norm.contains("verre") || norm.contains("ceramique")) {
            state.setPackageType("Fragile");
        } else if (norm.contains("electronique") || norm.contains("telephone") || norm.contains("pc") || norm.contains("ordinateur")) {
            state.setPackageType("Électronique");
        } else if (norm.contains("document") || norm.contains("papier") || norm.contains("lettre") || norm.contains("dossier")) {
            state.setPackageType("Document");
        } else if (norm.contains("alimentaire") || norm.contains("nourriture") || norm.contains("repas") || norm.contains("gateau")) {
            state.setPackageType("Alimentaire");
        }

        // 5. Extract Urgency
        if (norm.contains("urgent") || norm.contains("urgente") || norm.contains("express") || norm.contains("rapide") || norm.contains("tout de suite")) {
            state.setUrgent(true);
        }

        // 6. Extract Order ID for tracking
        Matcher orderMatcher = ORDER_ID_PATTERN.matcher(norm);
        if (orderMatcher.find()) {
            state.setTargetOrderId(orderMatcher.group(1));
        } else if (state.getActiveIntent() == AiIntent.TRACK_ORDER && state.getTargetOrderId() == null) {
            Matcher numMatcher = ISOLATED_NUMBER_PATTERN.matcher(norm);
            if (numMatcher.find()) {
                state.setTargetOrderId(numMatcher.group(1));
            }
        }
    }

    private static void extractWilayas(String norm, ConversationState state) {
        List<FoundWilaya> found = new ArrayList<>();

        for (String w : WILAYAS) {
            String normW = normalize(w);
            int idx = norm.indexOf(normW);
            while (idx != -1) {
                // Ensure word boundary
                boolean startOk = (idx == 0 || !Character.isLetterOrDigit(norm.charAt(idx - 1)));
                int endIdx = idx + normW.length();
                boolean endOk = (endIdx == norm.length() || !Character.isLetterOrDigit(norm.charAt(endIdx)));

                if (startOk && endOk) {
                    found.add(new FoundWilaya(w, idx));
                }
                idx = norm.indexOf(normW, idx + 1);
            }
        }

        if (found.isEmpty()) return;

        // Sort by occurrence index in the text
        Collections.sort(found, (a, b) -> Integer.compare(a.position, b.position));

        // Pattern 1: Explicit connectors like "de [Origin] a [Destination]" or "d [Origin] vers [Destination]"
        if (found.size() >= 2) {
            state.setOriginWilaya(found.get(0).name);
            state.setDestinationWilaya(found.get(1).name);
        } else if (found.size() == 1) {
            FoundWilaya single = found.get(0);
            int pos = single.position;
            String prefix = pos > 0 ? norm.substring(Math.max(0, pos - 10), pos).trim() : "";

            if (prefix.endsWith("vers") || prefix.endsWith("a") || prefix.endsWith("pour")) {
                if (state.getOriginWilaya() == null && state.getDestinationWilaya() != null) {
                    state.setOriginWilaya(single.name);
                } else {
                    state.setDestinationWilaya(single.name);
                }
            } else if (prefix.endsWith("de") || prefix.endsWith("d") || prefix.endsWith("depuis")) {
                state.setOriginWilaya(single.name);
            } else {
                // Assign to whichever is missing
                if (state.getOriginWilaya() == null) {
                    state.setOriginWilaya(single.name);
                } else if (state.getDestinationWilaya() == null && !single.name.equalsIgnoreCase(state.getOriginWilaya())) {
                    state.setDestinationWilaya(single.name);
                }
            }
        }
    }

    private static class FoundWilaya {
        final String name;
        final int position;

        FoundWilaya(String name, int position) {
            this.name = name;
            this.position = position;
        }
    }
}
