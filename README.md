# Wassil (وصيل) 📦🛵

> **Application Collaborative de Livraison Urbaine et Inter-Wilayas en Algérie**

Wassil est une plateforme collaborative de livraison connectant les expéditeurs, les livreurs indépendants et les administrateurs avec calcul d'itinéraire en direct, vérification d'identité par OCR, suivi cartographique en temps réel et gestion financière automatisée.

---

## 🌟 Fonctionnalités Principales

### 📦 Expéditeur (Sender)
- **Ajout de colis intuitif :** Choix d'adresses via carte Mapbox interactive, géolocalisation GPS en direct ou recherche textuelle avec autocomplétion des wilayas et communes algériennes.
- **Estimation intelligente :** Calcul dynamique de la distance routière (`km`), de la durée estimée (`min`), du tarif recommandé (`DA`) et recommandation automatique du véhicule optimal (Moto, Voiture, Fourgon, Camion) en fonction du poids et volume.
- **Photo de colis optionnelle :** Prise de photo ou sélection depuis la galerie avec compression automatique et stockage sécurisé sur Supabase Storage.
- **Portefeuille intégré :** Gestion du solde et rechargement de crédit avec montants prédéfinis (1 000 DA, 2 000 DA, 5 000 DA, etc.) et validation sécurisée.
- **Assistant Virtuel IA :** Assistant conversationnel pour estimer les tarifs, suivre les livraisons et répondre aux questions fréquentes.

### 🛵 Livreur (Courier)
- **Vérification d'identité KYC complète :** Téléversement et analyse OCR automatique (Google ML Kit) de :
  1. Carte Nationale d'Identité (**CIN**)
  2. Permis de conduire (**Permis**)
  3. Carte grise du véhicule (**Carte grise**)
- **Gestion des courses :** Consultation des demandes disponibles à proximité, acceptation et mise à jour des statuts en direct (*vers départ*, *colis récupéré*, *en route*, *livré*).
- **Gains & Retraits :** Crédit automatique des gains nets après déduction de la commission plateforme lors de la livraison, historique des transactions et formulaires de demande de retrait (virement CCP / BaridiMob / Espèces).

### 🛡️ Administrateur (Admin)
- **Tableau de bord temps réel :** Vue d'ensemble des KPI clés (commandes totales, chiffre d'affaires brut GMV, livreurs actifs, courses en cours).
- **Carte en direct Mapbox :** Visualisation cartographique en temps réel de tous les livreurs actifs et points de collecte/livraison.
- **Gestion des commissions plateforme :**
  - Affichage instantané du taux de prélèvement, des commissions perçues par la plateforme et des revenus nets distribués aux livreurs.
  - Modification interactive du taux de commission (presets 5%, 10%, 15%, 20%, 25% ou valeur personnalisée) avec simulation d'impact en temps réel et synchronisation immédiate sur la base de données Supabase.
- **Vérification KYC & Retraits :** File d'attente avec aperçu des documents téléversés, texte extrait par OCR, validation ou refus avec motifs.

---

## 🛠️ Stack Technique

- **Plateforme :** Android Native (Java 17, Android SDK 34/35)
- **Système de Build :** Gradle (Kotlin DSL `build.gradle.kts`)
- **Cartographie & Géocodage :** Mapbox Maps SDK v11, Mapbox Directions API, Mapbox Search SDK
- **Backend & Cloud :** [Supabase](https://supabase.com)
  - PostgreSQL 15+ avec Row Level Security (RLS)
  - Fonctions stockées PL/pgSQL (RPC)
  - Triggers automatisés pour le calcul des commissions et la distribution des soldes
  - Supabase Storage (buckets `kyc`, `parcels`)
- **Computer Vision & IA :** Google ML Kit Text Recognition (OCR sur l'appareil)
- **Réseau :** Retrofit 2, OkHttp 4, Gson

---

## 🚀 Installation & Configuration

### Prérequis
- Android Studio Ladybug (ou version plus récente)
- JDK 17+
- Compte Mapbox (avec un token de téléchargement secret pour les dépendances SDK v11)
- Instance Supabase configurée

### 1. Configuration des clés locales
Créez un fichier `local.properties` à la racine du projet (dans le dossier `Wassilapp/`) en vous basant sur `local.properties.example` :

```properties
sdk.dir=C\:/Users/VOTRE_NOM/AppData/Local/Android/Sdk

# Token Mapbox Secret (avec permissions Downloads:Read)
MAPBOX_DOWNLOADS_TOKEN=votre_token_secret_mapbox

# Identifiants Supabase
SUPABASE_URL=https://votre-projet.supabase.co
SUPABASE_ANON_KEY=votre_cle_anon_supabase
```

### 2. Déploiement de la base de données
Exécutez le script SQL complet disponible dans `supabase/schema.sql` sur l'éditeur SQL de votre projet Supabase. Ce script initialise :
- Les tables (`profiles`, `orders`, `offers`, `kyc_documents`, `withdrawals`, `platform_settings`)
- Les politiques de sécurité Row-Level Security (RLS)
- Les triggers de calcul de commissions et de crédit de solde
- Les fonctions RPC administratives sécurisées (`review_kyc_document`, `set_platform_commission`, etc.)

### 3. Compilation et Lancement
Depuis Android Studio ou en ligne de commande :

```bash
# Nettoyage
./gradlew clean

# Compilation APK Debug
./gradlew assembleDebug

# Installation sur appareil ou émulateur connecté
./gradlew installDebug
```

---

## 📁 Structure du Projet

```
Wassilapp/
├── app/
│   ├── src/main/java/com/example/wassilapp/
│   │   ├── activities/       # Écrans principaux (Auth, Home, Delivery, KYC, Admin...)
│   │   ├── adapters/         # Adaptateurs RecyclerView (Orders, KYC Review, Retraits...)
│   │   ├── ai/               # Moteur de recommandation de véhicule et assistant virtuel
│   │   ├── database/         # Cache SQLite local (DatabaseHelper)
│   │   ├── models/           # Modèles de données métier (User, Order, Profile...)
│   │   ├── remote/           # Clients API Retrofit, DTOs et Repositories Supabase
│   │   └── utils/            # Utilitaires (Estimateur de prix, SessionManager, OcrHelper...)
│   └── src/main/res/         # Layouts XML, Drawables, Menus, Thèmes et Valeurs
├── gradle/                   # Gradle Wrapper
├── supabase/
│   ├── schema.sql            # Schéma PostgreSQL complet et fonctions RPC
│   ├── wilaya_benchmarks.json # Benchmarks de distance et tarifs par wilaya
│   └── algerian_delivery_market_dataset.csv
├── local.properties.example  # Modèle de configuration locale
└── build.gradle.kts          # Configuration de build du projet
```

---

## 📄 Licence
Tous droits réservés © 2026 - Projet WASSIL Algérie.
