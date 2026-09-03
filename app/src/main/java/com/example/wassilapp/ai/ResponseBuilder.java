package com.example.wassilapp.ai;

import com.example.wassilapp.models.AiMessage;

/**
 * Builds structured knowledge and FAQ responses for the WASSIL platform.
 */
public class ResponseBuilder {

    public static AiMessage buildResponse(AiIntent intent) {
        switch (intent) {
            case COURIER_REGISTRATION:
                return buildCourierRegistrationResponse();

            case KYC_HELP:
                return buildKycHelpResponse();

            case WITHDRAWAL_HELP:
                return buildWithdrawalHelpResponse();

            case PAYMENT_HELP:
                return buildPaymentHelpResponse();

            case PROHIBITED_GOODS:
                return buildProhibitedGoodsResponse();

            case FRAGILE_GOODS:
                return buildFragileGoodsResponse();

            case CREATE_DELIVERY:
                return buildCreateDeliveryResponse();

            case GENERAL_FAQ:
                return buildGeneralFaqResponse();

            default:
                return buildFallbackResponse();
        }
    }

    private static AiMessage buildCourierRegistrationResponse() {
        String text = "🚴 **Devenir Livreur partenaire WASSIL**\n\n" +
                "Pour commencer à livrer et générer des revenus sur WASSIL :\n" +
                "1. Créez un compte avec le rôle **Livreur** (ou demandez le changement dans votre profil).\n" +
                "2. Renseignez votre type de véhicule (Moto, Voiture, Utilitaire, Camion).\n" +
                "3. **Validez votre KYC** en téléversant votre Carte Nationale d'Identité (CIN) et votre Permis de conduire.\n" +
                "4. Une fois votre dossier validé par l'administrateur, vous recevrez les demandes de livraison en temps réel dans votre Wilaya !";

        AiMessage msg = new AiMessage(text, false);
        msg.setHasActionCard(true);
        msg.setActionType(AiMessage.ACTION_OPEN_KYC);
        msg.setCardTitle("Vérification d'identité (KYC)");
        msg.setCardSubtitle("Téléversez CIN & Permis pour activer les livraisons");
        msg.setCardBadge("Requis");
        msg.setCardButtonText("Ouvrir mon KYC");
        return msg;
    }

    private static AiMessage buildKycHelpResponse() {
        String text = "📄 **Procédure de validation KYC (Identité)**\n\n" +
                "Conformément à la réglementation et pour la sécurité des expéditeurs :\n" +
                "• **Documents requis :**\n" +
                "  1. Pièce d'identité officielle (CIN ou Passeport biométrique recto/verso).\n" +
                "  2. Permis de conduire en cours de validité.\n" +
                "• **Délai de traitement :** Généralement vérifié sous 24h par l'équipe administrative.\n" +
                "• **Statut de votre compte :**\n" +
                "  - *En attente* : Document soumis, en cours d'examen.\n" +
                "  - *Vérifié* : Profil approuvé, vous pouvez accepter toutes les livraisons.\n" +
                "  - *Rejeté* : Photo illisible ou document expiré (vous pouvez le soumettre à nouveau).";

        AiMessage msg = new AiMessage(text, false);
        msg.setHasActionCard(true);
        msg.setActionType(AiMessage.ACTION_OPEN_KYC);
        msg.setCardTitle("Mes documents KYC");
        msg.setCardSubtitle("Gérer et téléverser mes pièces justificatives");
        msg.setCardBadge("Sécurité");
        msg.setCardButtonText("Accéder au KYC");
        return msg;
    }

    private static AiMessage buildWithdrawalHelpResponse() {
        String text = "💳 **Retraits et Solde Livreur**\n\n" +
                "• **Comment ça marche ?**\n" +
                "  Vos gains sur les livraisons payées par portefeuille sont crédités directement sur votre solde WASSIL.\n" +
                "• **Demande de retrait :**\n" +
                "  Vous pouvez demander un retrait partiel ou total depuis votre écran Portefeuille/Gains.\n" +
                "• **Modes de règlement :** Espèces en agence ou virement sécurisé.\n" +
                "• **Traitement :** Les demandes de retrait sont examinées et validées par un administrateur dans la section Gestion des retraits.";

        AiMessage msg = new AiMessage(text, false);
        msg.setHasActionCard(true);
        msg.setActionType(AiMessage.ACTION_OPEN_WITHDRAWALS);
        msg.setCardTitle("Portefeuille Livreur");
        msg.setCardSubtitle("Consultez vos gains et effectuez un retrait");
        msg.setCardBadge("Finance");
        msg.setCardButtonText("Voir mes retraits");
        return msg;
    }

    private static AiMessage buildPaymentHelpResponse() {
        return new AiMessage(
                "💵 **Modes de Paiement acceptés sur WASSIL**\n\n" +
                        "1. **Paiement en espèces (Cash on Delivery) :**\n" +
                        "   Le client paie le montant convenu directement au livreur lors de la réception du colis.\n\n" +
                        "2. **Portefeuille WASSIL (Wallet) :**\n" +
                        "   Paiement instantané depuis votre solde in-app avec débit automatique sécurisé et confirmation en temps réel.\n\n" +
                        "3. **Paiement électronique (CIB / Edahabia) :**\n" +
                        "   Prévu prochainement via la passerelle monétique certifiée SATIM.",
                false
        );
    }

    private static AiMessage buildProhibitedGoodsResponse() {
        return new AiMessage(
                "🚫 **Articles et Marchandises strictement interdits**\n\n" +
                        "Pour des raisons légales et de sécurité en Algérie, il est formellement interdit de transporter :\n" +
                        "❌ Armes, munitions, feux d'artifice ou explosifs\n" +
                        "❌ Matières inflammables, acides ou produits chimiques toxiques\n" +
                        "❌ Stupéfiants, drogues et substances illicites\n" +
                        "❌ Médicaments sans ordonnance ou substances sous contrôle strict\n" +
                        "❌ Sommes importantes d'argent liquide ou métaux précieux\n" +
                        "❌ Marchandises de contrebande ou contrefaites\n\n" +
                        "Tout colis suspect peut être refusé par le livreur et signalé aux autorités.",
                false
        );
    }

    private static AiMessage buildFragileGoodsResponse() {
        return new AiMessage(
                "🍷 **Conseils pour l'envoi de colis Fragiles**\n\n" +
                        "Si vous expédiez de la verrerie, de la céramique ou du matériel électronique :\n" +
                        "1. **Sélectionnez le type 'Fragile'** lors de la création de la livraison (+25% sur la prime d'assurance manipulation).\n" +
                        "2. L'algorithme sélectionnera automatiquement une **Voiture** plutôt qu'une moto pour une meilleure stabilité.\n" +
                        "3. Utilisez du papier bulle et mentionnez clairement la mention *FRAGILE* sur le carton extérieur.",
                false
        );
    }

    private static AiMessage buildCreateDeliveryResponse() {
        AiMessage msg = new AiMessage(
                "📦 Vous souhaitez expédier un colis ?\n\n" +
                        "Cliquez ci-dessous pour ouvrir le formulaire de nouvelle livraison, ou dites-moi par exemple : " +
                        "*'Combien pour envoyer 5kg d'Alger à Sétif ?'* pour calculer votre estimation au préalable !",
                false
        );
        msg.setHasActionCard(true);
        msg.setActionType(AiMessage.ACTION_CREATE_DELIVERY);
        msg.setCardTitle("Nouvelle commande de livraison");
        msg.setCardSubtitle("Renseignez les adresses et les détails du colis");
        msg.setCardBadge("Rapide");
        msg.setCardButtonText("Créer une livraison");
        return msg;
    }

    private static AiMessage buildGeneralFaqResponse() {
        return new AiMessage(
                "👋 **Bienvenue sur l'Assistant WASSIL !**\n\n" +
                        "Je suis à votre service pour vous guider sur l'application :\n" +
                        "• **Estimer un prix** : Posez une question comme *'Combien pour 3kg d'Alger à Oran ?'*\n" +
                        "• **Suivre un colis** : Dites *'Où est mon colis ?'* ou *'Statut de ma commande'*\n" +
                        "• **Livreurs & KYC** : Renseignements sur l'inscription, la validation des permis et les retraits de solde\n" +
                        "• **Règles d'envoi** : Colis volumineux, fragiles ou interdits\n\n" +
                        "Que souhaitez-vous savoir ?",
                false
        );
    }

    private static AiMessage buildFallbackResponse() {
        return new AiMessage(
                "Je n'ai pas tout à fait compris votre demande.\n\n" +
                        "Vous pouvez me demander par exemple :\n" +
                        "• *'Où est mon colis ?'*\n" +
                        "• *'Combien coûte l'envoi de 5kg d'Alger à Constantine ?'*\n" +
                        "• *'Comment valider mes documents KYC ?'*\n" +
                        "• *'Comment retirer mes gains de livraison ?'*",
                false
        );
    }
}
