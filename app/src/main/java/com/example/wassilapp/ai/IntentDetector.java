package com.example.wassilapp.ai;

/**
 * Rule-based Intent Detector for the WASSIL platform.
 * Determines the primary user intent using normalized phrase matching and conversation context.
 */
public class IntentDetector {

    public static AiIntent detectIntent(String rawInput, ConversationState state) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return AiIntent.UNKNOWN;
        }

        String norm = EntityExtractor.normalize(rawInput);

        // 1. Check for explicit exit / reset keywords
        if (norm.equals("annuler") || norm.equals("reset") || norm.equals("stop") || norm.equals("quitter")) {
            state.reset();
            return AiIntent.GENERAL_FAQ;
        }

        // 2. High Priority Explicit Action Intents

        // Track Order
        if (containsAny(norm,
                "ou est mon colis", "ou est ma commande", "suivre mon colis", "suivre ma commande",
                "suivi colis", "suivi de commande", "statut de ma commande", "statut commande",
                "etat livraison", "etat de mon colis", "position de mon colis", "localisation",
                "arrive quand", "win rah", "win raho", "وين راه", "وين راها")) {
            return AiIntent.TRACK_ORDER;
        }

        // List Orders
        if (containsAny(norm,
                "mes commandes", "mes livraisons", "historique", "liste de mes colis",
                "commandes en cours", "livraisons actives", "mes colis")) {
            return AiIntent.LIST_ORDERS;
        }

        // Price Estimate (Questions about tariffs, costs, DZD quotes, or direct route + weight queries)
        if (containsAny(norm,
                "combien", "prix", "tarif", "cout", "coute", "estimer", "estimation",
                "devis", "prix livraison", "chhal", "tarification", "شحال", "calculer prix")
                || ((state.getOriginWilaya() != null || state.getDestinationWilaya() != null) && state.getWeightKg() != null)) {
            return AiIntent.PRICE_ESTIMATE;
        }

        // Create Delivery
        if (containsAny(norm,
                "creer une livraison", "nouvelle livraison", "envoyer un colis",
                "commander une livraison", "faire une livraison", "demander un livreur",
                "poster un colis", "passer commande")) {
            return AiIntent.CREATE_DELIVERY;
        }

        // Courier Registration / Work
        if (containsAny(norm,
                "devenir livreur", "travailler comme livreur", "inscription livreur",
                "rejoindre les livreurs", "postuler", "recrutement livreur", "gagner de l argent",
                "travailler avec wassil", "je veux livrer")) {
            return AiIntent.COURIER_REGISTRATION;
        }

        // KYC Verification (Identity & License)
        if (containsAny(norm,
                "kyc", "carte d identite", "permis de conduire", "cin", "validation compte",
                "verifier mon compte", "documents requis", "verification profil", "document rejete",
                "document refuse", "televerser document")) {
            return AiIntent.KYC_HELP;
        }

        // Withdrawals & Courier Payouts
        if (containsAny(norm,
                "retrait", "retirer mes gains", "retirer mon solde", "retrait argent",
                "virement", "demande de retrait", "recuperer mon argent", "delai retrait",
                "solde livreur", "comment retirer")) {
            return AiIntent.WITHDRAWAL_HELP;
        }

        // Payments & Wallet
        if (containsAny(norm,
                "paiement", "payer", "especes", "cash", "portefeuille", "wallet",
                "comment payer", "edahabia", "cib", "monnaie", "reglement")) {
            return AiIntent.PAYMENT_HELP;
        }

        // Prohibited Goods
        if (containsAny(norm,
                "interdit", "produits interdits", "marchandise interdite", "interdiction",
                "objets interdits", "armes", "drogue", "alcool", "produits chimiques", "danger")) {
            return AiIntent.PROHIBITED_GOODS;
        }

        // Fragile Goods & Packaging
        if (containsAny(norm,
                "fragile", "emballage", "colis fragile", "verre", "protection colis",
                "marchandise fragile", "casser", "precautions")) {
            return AiIntent.FRAGILE_GOODS;
        }

        // General Platform FAQs
        if (containsAny(norm,
                "wassil", "c est quoi wassil", "comment ca marche", "qui etes vous",
                "bonjour", "salut", "salam", "aide", "help", "support", "contact",
                "service client", "numero", "application")) {
            return AiIntent.GENERAL_FAQ;
        }

        // 3. Multi-turn Continuity Check:
        // If user was in PRICE_ESTIMATE state and provided an answer to a slot (e.g., "5kg" or "Oran")
        if (state.getActiveIntent() == AiIntent.PRICE_ESTIMATE) {
            if (state.getWeightKg() != null || state.getOriginWilaya() != null || state.getDestinationWilaya() != null) {
                return AiIntent.PRICE_ESTIMATE;
            }
        }

        // If user was in TRACK_ORDER state and provided an order number (e.g., "#3" or "3")
        if (state.getActiveIntent() == AiIntent.TRACK_ORDER && state.getTargetOrderId() != null) {
            return AiIntent.TRACK_ORDER;
        }

        return AiIntent.UNKNOWN;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
