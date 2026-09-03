# Wassil (وصيل) 📦🛵

> **Collaborative Urban & Inter-Wilaya Delivery Platform with Edge AI, Computer Vision, and Real-Time Geospatial Intelligence**

Wassil is an end-to-end collaborative logistics and delivery mobile application engineered for the Algerian market. It seamlessly connects senders, couriers, and platform administrators through real-time Mapbox route optimization, on-device OCR identity verification, automated financial triggers, and a hybrid conversational AI engine with dialectal NLP support.

---

## 🧠 Artificial Intelligence & Machine Learning Architecture

As an AI-driven platform, Wassil incorporates edge computer vision, dialectal natural language understanding (NLU), dialogue state tracking, and empirical data-driven pricing models.

```
                      ┌──────────────────────────────────────────────┐
                      │             User Interaction / Chat          │
                      │  (French, English, Algerian Darija / Arabizi) │
                      └──────────────────────┬───────────────────────┘
                                             │
                                             ▼
                      ┌──────────────────────────────────────────────┐
                      │       Rule-Based / Deterministic NLU         │
                      │     IntentDetector & EntityExtractor         │
                      └──────┬────────────────────────────────┬──────┘
                             │ (Slots filled / High confidence)│ (Complex / Open query)
                             ▼                                ▼
              ┌─────────────────────────────┐  ┌─────────────────────────────┐
              │  Dialogue State Tracker     │  │ Cloud LLM Edge Microservice │
              │     (ConversationState)     │  │   (Supabase Edge Functions) │
              └──────────────┬──────────────┘  └──────────────┬──────────────┘
                             │                                │
                             ▼                                ▼
              ┌──────────────────────────────────────────────────────────────┐
              │        Response Synthesizer & Operational Actions            │
              │  (Price Estimation, Order Tracking, Status Updates, FAQs)    │
              └──────────────────────────────────────────────────────────────┘
```

### 1. Dialectal NLU & Multilingual Intent Classification
The core conversational engine (`com.example.wassilapp.ai`) addresses the linguistic diversity of Algeria:
- **Multilingual Natural Language Processing:** Simultaneously parses Modern Standard Arabic, French, English, and Algerian Darija in both Arabic script (e.g., `وين راه`, `شحال`) and Arabizi/Latin script (e.g., `win rah`, `chhal`, `kifah nlivri`).
- **Intent Recognition (`IntentDetector.java`):** Classifies user inputs into structured system intents (`PRICE_ESTIMATE`, `TRACK_ORDER`, `LIST_ORDERS`, `CREATE_DELIVERY`, `COURIER_SIGNUP`, `GENERAL_FAQ`).
- **Slot Filling & Entity Extraction (`EntityExtractor.java`):** Employs regex-based token extractors and normalized gazetteers to identify:
  - Algerian Wilayas (58 wilayas and major communes)
  - Tracking numbers (`WSL-2026-XXXXX`)
  - Numeric payload weights (`kg`, `g`) and parcel dimensions
  - Delivery modes (Home delivery vs. Stopdesk pickup)

### 2. Dialogue State Tracking (DST) & Context Management
- **State Persistence (`ConversationState.java`):** Tracks conversational context across multi-turn interactions.
- **Dynamic Slot Filling:** If a user expresses an intent with missing parameters (e.g., *"How much to send a package?"*), the system retains state and generates targeted prompts asking for missing entities (*origin*, *destination*, *weight*).
- **Hybrid Edge/Cloud Orchestration (`OnlineLLMService.java`):**
  - **Edge-First Determinism:** Fast, offline-resilient matching handles mission-critical operational flows with zero network latency.
  - **Cloud LLM Fallback:** Complex queries are routed to an LLM microservice deployed on Supabase Edge Functions.

### 3. On-Device Computer Vision & Document OCR
- **Google ML Kit Text Recognition:** Integrated into the Courier KYC pipeline (`KycActivity.java`, `OcrHelper.java`).
- **Edge Inference:** Directly extracts biometric identifiers, full names, dates of birth, and document numbers from identity cards (**CIN**), driver's licenses (**Permis**), and vehicle registration documents (**Carte grise**).
- **Privacy by Design:** Text extraction executes locally on the mobile GPU/NPU before uploading compressed assets to encrypted Supabase buckets, minimizing sensitive visual data exposure.

### 4. Smart Vehicle Recommendation & Dynamic Pricing Model
- **Constraint-Based Vehicle Classifier:** Evaluates dimensional and mass bounds ($w \le 15\,\text{kg} \to \text{Moto}$, $15 < w \le 100\,\text{kg} \to \text{Car}$, $100 < w \le 800\,\text{kg} \to \text{Van}$, $w > 800\,\text{kg} \to \text{Truck}$) while factoring in urban traffic accessibility.
- **Empirical Market Calibration:** Pricing algorithms cross-reference empirical benchmarks (`wilaya_benchmarks.json`, `algerian_delivery_market_dataset.csv`) across northern, highland, and southern zones to generate competitive, realistic delivery quotes in Algerian Dinars (DZD).

---

## 🌟 Core Application Features

### 📦 Senders (Expéditeurs)
- **Interactive Parcel Creation:** Geocoded address selection via interactive Mapbox map, live GPS location, or autocompleted wilaya/commune search.
- **Instant Pre-Trip Estimation:** Persistent UI card displaying precise route distance (`km`), estimated transit time (`min`), cost estimate (`DA`), and recommended vehicle.
- **Optional Parcel Photography:** Capture or pick package photos with automatic on-device compression and cloud upload.
- **In-App Wallet:** Secure balance management with pre-configured top-up denominations (1 000 DA, 2 000 DA, 5 000 DA).

### 🛵 Couriers (Livreurs)
- **3-Tier KYC Identity Verification:** Automated OCR submission for CIN, Driver's License, and Vehicle Carte Grise.
- **Order Dispatch & Status Lifecycle:** Real-time visibility into nearby delivery requests with stage transitions (*en route to pickup*, *parcel collected*, *out for delivery*, *delivered*).
- **Automated Net Earnings:** Platform commission is automatically deducted upon delivery completion via database triggers, crediting courier net payouts immediately.

### 🛡️ Administrators
- **Real-Time Fleet Telemetry:** Interactive Mapbox dashboard plotting active couriers and order origins/destinations.
- **Dynamic Platform Commission Control:**
  - Real-time dashboard KPI breakdown (Total GMV, Platform Revenue Share, Net Courier Payouts).
  - Commission rate adjustment modal with quick presets (5%, 10%, 15%, 20%, 25%), custom percentage input, and live financial simulation.
- **KYC & Withdrawal Approval Queue:** Audit queue displaying extracted OCR text and document previews for instant verification.

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| **Mobile Client** | Android Native (Java 17, SDK 34/35, Kotlin DSL Gradle) |
| **Geospatial & Mapping** | Mapbox Maps SDK v11, Mapbox Directions API, Mapbox Search SDK |
| **Backend & Cloud DB** | Supabase (PostgreSQL 15+, Row-Level Security, RPC Functions, Realtime) |
| **Machine Learning & Vision** | Google ML Kit (Text Recognition OCR v2) |
| **AI Dialogue Engine** | Hybrid NLU Pipeline (Rule-based Slot Filler + Supabase Edge LLM) |
| **Networking & HTTP** | Retrofit 2, OkHttp 4, Gson |

---

## 🚀 Setup & Installation

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17+
- Mapbox Account (Secret download token with `Downloads:Read` scope)
- Supabase Project

### 1. Local Environment Configuration
Create a `local.properties` file in the `Wassilapp/` directory by copying `local.properties.example`:

```properties
sdk.dir=C\:/Users/YOUR_USERNAME/AppData/Local/Android/Sdk

# Mapbox Secret Download Token (for Maven dependency resolution)
MAPBOX_DOWNLOADS_TOKEN=your_mapbox_secret_download_token

# Supabase API Credentials
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key
```

> ⚠️ **Security Notice:** Never commit `local.properties` to version control. It is protected by `.gitignore`.

### 2. Database Provisioning
Run the SQL schema located at `supabase/schema.sql` inside your Supabase SQL Editor. This provisions:
- Relational schema (`profiles`, `orders`, `offers`, `kyc_documents`, `withdrawals`, `platform_settings`)
- Row-Level Security (RLS) policies
- Automated delivery payout triggers (`credit_courier_on_delivery`)
- Administrative stored procedures (`set_platform_commission`, `review_kyc_document`)

### 3. Build & Run
From the `Wassilapp/` directory:

```bash
# Clean previous build artifacts
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on connected emulator or device
./gradlew installDebug
```

---

## 📁 Repository Structure

```
Wassilapp/
├── app/
│   ├── src/main/java/com/example/wassilapp/
│   │   ├── activities/          # UI Controllers (Auth, Map, NewDelivery, KYC, Admin...)
│   │   ├── adapters/            # RecyclerView Adapters (Orders, KYC Review, Withdrawals...)
│   │   ├── ai/                  # AI Conversational Engine, NLU, and Dialogue State Tracker
│   │   │   ├── AiAssistantEngine.java
│   │   │   ├── AiIntent.java
│   │   │   ├── ConversationState.java
│   │   │   ├── EntityExtractor.java
│   │   │   ├── IntentDetector.java
│   │   │   ├── OnlineLLMService.java
│   │   │   └── PriceEstimateHandler.java
│   │   ├── database/            # Local SQLite database helpers & caching
│   │   ├── models/              # Domain entities (User, Order, Profile, AiMessage...)
│   │   ├── remote/              # Retrofit interfaces, Supabase DTOs, and repositories
│   │   └── utils/               # PriceEstimator, ML Kit OcrHelper, SessionManager
│   └── src/main/res/            # Layouts, vector drawables, themes, and animations
├── supabase/
│   ├── schema.sql               # Production PostgreSQL DDL, triggers, and RPCs
│   ├── wilaya_benchmarks.json   # Wilaya pricing and distance benchmarks
│   └── algerian_delivery_market_dataset.csv
├── local.properties.example     # Configuration template for developers
└── build.gradle.kts             # Project build configuration
```

---

## 📄 License & Attribution
Developed with ❤️ for Algerian collaborative logistics.  
All Rights Reserved © 2026 - Wassil Project.
