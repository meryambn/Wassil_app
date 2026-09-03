package com.example.wassilapp.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.geo.GeoPoint;
import com.example.wassilapp.geo.GeoRepository;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.OrderRepository;
import com.example.wassilapp.remote.dto.NewOrderRequest;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.utils.PriceEstimator;
import com.example.wassilapp.utils.SessionManager;
import java.util.Locale;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class NewDeliveryActivity extends AppCompatActivity {

    private static final int REQ_PICK_PICKUP = 4001;
    private static final int REQ_PICK_DROP = 4002;

    private static final int REQ_PICK_PACKAGE_PHOTO = 4003;
    private static final int MAX_PHOTO_DIMENSION = 1280;
    private static final int PHOTO_JPEG_QUALITY = 80;

    private DatabaseHelper db;
    private SessionManager session;
    private final OrderRepository orderRepository = new OrderRepository();
    private User currentUser;
    private double estimatedPrice;
    private int estimatedTime;

    // UI Components
    private TextInputEditText etWeight, etDimensions, etInstructions;
    private MaterialCardView cardStandard, cardExpress, cardMoto, cardVoiture, cardCamion, cardPickupAddress, cardDeliveryAddress;
    private ChipGroup chipGroupPackageType;
    private Chip chipColis, chipDocument, chipElectronique, chipAlimentaire, chipFragile, chipAutre;
    private Button btnCalculate, btnConfirm;
    private TextView tvEstimatedPrice, tvEstimatedTime, tvEstimatedDistance, tvPickupAddress, tvDeliveryAddress;
    private LinearLayout layoutEstimate;
    private TextView btnBack;

    // Recommended Vehicle UI
    private TextView tvRecommendedVehicle, tvVehicleReason, tvRecommendedVehicleIcon;
    private Button btnApplyRecommendedVehicle;
    private String currentRecommendedVehicle = null;

    // Photo Components
    private MaterialCardView cardPackagePhoto;
    private LinearLayout layoutPhotoPlaceholder, layoutPhotoPreview;
    private ImageView ivPackagePreview;
    private TextView btnChangePhoto, btnRemovePhoto;
    private android.net.Uri selectedPhotoUri = null;
    private android.graphics.Bitmap selectedPhotoBitmap = null;

    // Selected values
    private String selectedUrgency = "Standard";
    private String selectedDeliveryMethod = "Moto";
    private String selectedPackageType = "Colis";
    private String pickupAddress = "";
    private String deliveryAddress = "";

    // Picked points. Until both exist there is no route to measure, which is why the
    // estimate button cannot produce a real price before the second pin is dropped.
    private GeoPoint pickupPoint;
    private GeoPoint dropPoint;

    private final GeoRepository geo = new GeoRepository();
    /** Last measured route. Zero distance means "not measured yet". */
    private double routeKm;
    private int routeMinutes;
    /** False when the figure came from the offline fallback rather than the road network. */
    private boolean routeFromRoadNetwork;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_delivery);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        // Add null check for currentUser
        int userId = session.getUserId();
        if (userId != -1) {
            currentUser = db.getUserById(userId);
            if (currentUser == null) {
                Toast.makeText(this, "Erreur: Utilisateur non trouvé", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, "Veuillez vous connecter", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        setupPhotoListeners();
        setDefaultSelections();
        applyIntentPrefills();
    }

    private void initViews() {
        etWeight = findViewById(R.id.etWeight);
        etDimensions = findViewById(R.id.etDimensions);
        etInstructions = findViewById(R.id.etInstructions);

        cardStandard = findViewById(R.id.cardStandard);
        cardExpress = findViewById(R.id.cardExpress);
        cardMoto = findViewById(R.id.cardMoto);
        cardVoiture = findViewById(R.id.cardVoiture);
        cardCamion = findViewById(R.id.cardCamion);
        cardPickupAddress = findViewById(R.id.cardPickupAddress);
        cardDeliveryAddress = findViewById(R.id.cardDeliveryAddress);

        chipGroupPackageType = findViewById(R.id.chipGroupPackageType);
        chipColis = findViewById(R.id.chipColis);
        chipDocument = findViewById(R.id.chipDocument);
        chipElectronique = findViewById(R.id.chipElectronique);
        chipAlimentaire = findViewById(R.id.chipAlimentaire);
        chipFragile = findViewById(R.id.chipFragile);
        chipAutre = findViewById(R.id.chipAutre);

        btnCalculate = findViewById(R.id.btnCalculate);
        btnConfirm = findViewById(R.id.btnConfirm);
        tvEstimatedPrice = findViewById(R.id.tvEstimatedPrice);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvEstimatedDistance = findViewById(R.id.tvEstimatedDistance);
        layoutEstimate = findViewById(R.id.layoutEstimate);
        tvPickupAddress = findViewById(R.id.tvPickupAddress);
        tvDeliveryAddress = findViewById(R.id.tvDeliveryAddress);
        btnBack = findViewById(R.id.btnBack);

        // Recommended Vehicle
        tvRecommendedVehicle = findViewById(R.id.tvRecommendedVehicle);
        tvVehicleReason = findViewById(R.id.tvVehicleReason);
        tvRecommendedVehicleIcon = findViewById(R.id.tvRecommendedVehicleIcon);
        btnApplyRecommendedVehicle = findViewById(R.id.btnApplyRecommendedVehicle);

        // Photo views
        cardPackagePhoto = findViewById(R.id.cardPackagePhoto);
        layoutPhotoPlaceholder = findViewById(R.id.layoutPhotoPlaceholder);
        layoutPhotoPreview = findViewById(R.id.layoutPhotoPreview);
        ivPackagePreview = findViewById(R.id.ivPackagePreview);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);
    }

    private void setDefaultSelections() {
        // Set default selection for urgency (Standard)
        cardStandard.setStrokeWidth(4);
        cardStandard.setStrokeColor(ContextCompat.getColor(this, R.color.primary));

        // Set default selection for delivery method (Moto)
        cardMoto.setStrokeWidth(4);
        cardMoto.setStrokeColor(ContextCompat.getColor(this, R.color.primary));

        // Set default package type (Colis)
        chipColis.setChecked(true);
    }

    private void applyIntentPrefills() {
        Intent intent = getIntent();
        if (intent == null) return;

        double weight = intent.getDoubleExtra("extra_weight", 0);
        if (weight > 0 && etWeight != null) {
            etWeight.setText(String.format(Locale.ROOT, "%.1f", weight));
        }

        String pickup = intent.getStringExtra("extra_pickup_wilaya");
        if (pickup != null && !pickup.isEmpty()) {
            pickupAddress = pickup;
            if (tvPickupAddress != null) {
                tvPickupAddress.setText(pickup);
            }
        }

        String drop = intent.getStringExtra("extra_drop_wilaya");
        if (drop != null && !drop.isEmpty()) {
            deliveryAddress = drop;
            if (tvDeliveryAddress != null) {
                tvDeliveryAddress.setText(drop);
            }
        }

        String pkgType = intent.getStringExtra("extra_package_type");
        if (pkgType != null) {
            switch (pkgType) {
                case "Document":
                    chipDocument.setChecked(true);
                    selectedPackageType = "Document";
                    break;
                case "Électronique":
                case "Electronique":
                    chipElectronique.setChecked(true);
                    selectedPackageType = "Électronique";
                    break;
                case "Alimentaire":
                    chipAlimentaire.setChecked(true);
                    selectedPackageType = "Alimentaire";
                    break;
                case "Fragile":
                    chipFragile.setChecked(true);
                    selectedPackageType = "Fragile";
                    break;
                default:
                    chipColis.setChecked(true);
                    selectedPackageType = "Colis";
                    break;
            }
        }

        boolean isUrgent = intent.getBooleanExtra("extra_urgent", false);
        if (isUrgent) {
            selectedUrgency = "Express";
            updateCardSelection(cardExpress, cardStandard);
        }

        String dims = intent.getStringExtra("extra_dimensions");
        if (dims != null && !dims.isEmpty() && etDimensions != null) {
            etDimensions.setText(dims);
        }
    }

    private void setupListeners() {
        // Urgency selection
        cardStandard.setOnClickListener(v -> {
            selectedUrgency = "Standard";
            updateCardSelection(cardStandard, cardExpress);
        });

        cardExpress.setOnClickListener(v -> {
            selectedUrgency = "Express";
            updateCardSelection(cardExpress, cardStandard);
        });

        // Delivery method selection
        cardMoto.setOnClickListener(v -> {
            selectedDeliveryMethod = "Moto";
            updateCardSelection(cardMoto, cardVoiture, cardCamion);
        });

        cardVoiture.setOnClickListener(v -> {
            selectedDeliveryMethod = "Voiture";
            updateCardSelection(cardVoiture, cardMoto, cardCamion);
        });

        cardCamion.setOnClickListener(v -> {
            selectedDeliveryMethod = "Camion";
            updateCardSelection(cardCamion, cardMoto, cardVoiture);
        });

        // Package type selection
        chipGroupPackageType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipColis) selectedPackageType = "Colis";
            else if (checkedId == R.id.chipDocument) selectedPackageType = "Document";
            else if (checkedId == R.id.chipElectronique) selectedPackageType = "Électronique";
            else if (checkedId == R.id.chipAlimentaire) selectedPackageType = "Alimentaire";
            else if (checkedId == R.id.chipFragile) selectedPackageType = "Fragile";
            else if (checkedId == R.id.chipAutre) selectedPackageType = "Autre";
        });

        // Address selection
        cardPickupAddress.setOnClickListener(v -> openPicker(true));
        cardDeliveryAddress.setOnClickListener(v -> openPicker(false));

        // Calculate price button
        btnCalculate.setOnClickListener(v -> calculateEstimate());

        // Confirm button
        btnConfirm.setOnClickListener(v -> {
            if (validateOrder()) {
                createOrder();
            }
        });

        // Back button
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupPhotoListeners() {
        cardPackagePhoto.setOnClickListener(v -> pickPackagePhoto());
        btnChangePhoto.setOnClickListener(v -> pickPackagePhoto());
        btnRemovePhoto.setOnClickListener(v -> clearPackagePhoto());

        if (btnApplyRecommendedVehicle != null) {
            btnApplyRecommendedVehicle.setOnClickListener(v -> {
                if (currentRecommendedVehicle != null) {
                    if ("Moto".equalsIgnoreCase(currentRecommendedVehicle)) {
                        selectedDeliveryMethod = "Moto";
                        updateCardSelection(cardMoto, cardVoiture, cardCamion);
                    } else if ("Voiture".equalsIgnoreCase(currentRecommendedVehicle)) {
                        selectedDeliveryMethod = "Voiture";
                        updateCardSelection(cardVoiture, cardMoto, cardCamion);
                    } else if ("Camion".equalsIgnoreCase(currentRecommendedVehicle)) {
                        selectedDeliveryMethod = "Camion";
                        updateCardSelection(cardCamion, cardMoto, cardVoiture);
                    }
                    Toast.makeText(this, "Véhicule sélectionné : " + currentRecommendedVehicle, Toast.LENGTH_SHORT).show();
                    // Recalculate ETA for selected vehicle
                    String weightStr = etWeight.getText() != null ? etWeight.getText().toString().trim() : "";
                    try {
                        double w = Double.parseDouble(weightStr);
                        applyEstimate(w);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void pickPackagePhoto() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(pick, "Choisir une photo du colis"), REQ_PICK_PACKAGE_PHOTO);
    }

    private void clearPackagePhoto() {
        selectedPhotoUri = null;
        if (selectedPhotoBitmap != null) {
            selectedPhotoBitmap.recycle();
            selectedPhotoBitmap = null;
        }
        layoutPhotoPreview.setVisibility(View.GONE);
        layoutPhotoPlaceholder.setVisibility(View.VISIBLE);
        ivPackagePreview.setImageDrawable(null);
    }

    private android.graphics.Bitmap decodeScaled(android.net.Uri uri) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(in, null, bounds);
            }

            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / sample > MAX_PHOTO_DIMENSION) {
                sample *= 2;
            }

            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                return android.graphics.BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private byte[] compressPhoto(android.graphics.Bitmap bitmap) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out);
            return out.toByteArray();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void updateCardSelection(MaterialCardView selected, MaterialCardView... others) {
        selected.setStrokeWidth(4);
        selected.setStrokeColor(ContextCompat.getColor(this, R.color.primary));
        for (MaterialCardView card : others) {
            card.setStrokeWidth(0);
        }
    }

    private void openPicker(boolean isPickup) {
        Intent intent = new Intent(this, AddressPickerActivity.class);
        intent.putExtra(AddressPickerActivity.EXTRA_TITLE,
                isPickup ? "Adresse de ramassage" : "Adresse de livraison");

        // Reopening on the previous pick saves the user re-finding their street.
        GeoPoint existing = isPickup ? pickupPoint : dropPoint;
        if (existing != null) {
            intent.putExtra(AddressPickerActivity.EXTRA_LAT, existing.lat);
            intent.putExtra(AddressPickerActivity.EXTRA_LNG, existing.lng);
        }
        startActivityForResult(intent, isPickup ? REQ_PICK_PICKUP : REQ_PICK_DROP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == REQ_PICK_PACKAGE_PHOTO) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                android.graphics.Bitmap bmp = decodeScaled(uri);
                if (bmp != null) {
                    selectedPhotoUri = uri;
                    selectedPhotoBitmap = bmp;
                    ivPackagePreview.setImageBitmap(bmp);
                    layoutPhotoPlaceholder.setVisibility(View.GONE);
                    layoutPhotoPreview.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Photo du colis ajoutée", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Impossible de charger l'image", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }

        if (requestCode != REQ_PICK_PICKUP && requestCode != REQ_PICK_DROP) {
            return;
        }

        GeoPoint point = new GeoPoint(
                data.getDoubleExtra(AddressPickerActivity.EXTRA_LAT, 0),
                data.getDoubleExtra(AddressPickerActivity.EXTRA_LNG, 0),
                data.getStringExtra(AddressPickerActivity.EXTRA_ADDRESS),
                data.getStringExtra(AddressPickerActivity.EXTRA_WILAYA));

        if (requestCode == REQ_PICK_PICKUP) {
            pickupPoint = point;
            pickupAddress = point.address;
            tvPickupAddress.setText(point.address);
        } else {
            dropPoint = point;
            deliveryAddress = point.address;
            tvDeliveryAddress.setText(point.address);
        }

        // Any change of endpoint invalidates the measured route, and with it the
        // price. Clearing it here stops a stale figure being carried into the order.
        routeKm = 0;
        routeMinutes = 0;
        layoutEstimate.setVisibility(View.GONE);
        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);
    }

    private void calculateEstimate() {
        String weightStr = etWeight.getText().toString().trim();
        if (weightStr.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer le poids", Toast.LENGTH_SHORT).show();
            return;
        }

        double weight;
        try {
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Poids invalide", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate weight limits
        if (selectedDeliveryMethod.equals("Moto") && weight > 10) {
            Toast.makeText(this, "Poids trop élevé pour une moto (max 10kg)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDeliveryMethod.equals("Voiture") && weight > 50) {
            Toast.makeText(this, "Poids trop élevé pour une voiture (max 50kg)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDeliveryMethod.equals("Camion") && weight > 500) {
            Toast.makeText(this, "Poids trop élevé pour un camion (max 500kg)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pickupPoint == null) {
            Toast.makeText(this, "Veuillez choisir l'adresse de ramassage", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dropPoint == null) {
            Toast.makeText(this, "Veuillez choisir l'adresse de livraison", Toast.LENGTH_SHORT).show();
            return;
        }

        // Both points picked: route through Mapbox Directions, falling back to
        // great-circle road estimation if offline.
        final double weightKg = weight;
        btnCalculate.setEnabled(false);
        geo.route(pickupPoint.lat, pickupPoint.lng, dropPoint.lat, dropPoint.lng, result -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            btnCalculate.setEnabled(true);
            routeKm = result.distanceKm;
            routeMinutes = result.minutes;
            routeFromRoadNetwork = result.fromRoadNetwork;
            applyEstimate(weightKg);
        });
    }

    /** Turns the measured route and parcel features into an AI-calibrated price, ETA, and vehicle advice. */
    private void applyEstimate(double weight) {
        PriceEstimator.EstimateParams params = new PriceEstimator.EstimateParams(routeKm, weight, selectedPackageType);

        // Parse optional dimensions (LxWxH in cm)
        String dimStr = etDimensions.getText() != null ? etDimensions.getText().toString() : "";
        double[] dims = PriceEstimator.parseDimensions(dimStr);
        if (dims != null) {
            params.lengthCm = dims[0];
            params.widthCm = dims[1];
            params.heightCm = dims[2];
        }

        params.isUrgent = "Express".equals(selectedUrgency);
        params.selectedVehicle = selectedDeliveryMethod;

        PriceEstimator.EstimateResult res = PriceEstimator.estimateDetailed(params);

        estimatedPrice = res.estimatedPrice;

        // Use Mapbox route minutes if available, otherwise use calibrated estimator minutes
        int baseTime = routeMinutes > 0 ? routeMinutes : res.estimatedMinutes;
        if ("Express".equals(selectedUrgency)) {
            baseTime = Math.max((int) (baseTime * 0.75), 15);
        }
        if ("Moto".equals(selectedDeliveryMethod)) {
            baseTime = (int) (baseTime * 0.85);
        } else if ("Camion".equals(selectedDeliveryMethod)) {
            baseTime = (int) (baseTime * 1.25);
        }
        estimatedTime = baseTime;

        if (tvEstimatedDistance != null) {
            tvEstimatedDistance.setText(String.format(java.util.Locale.getDefault(), "%.1f km", routeKm));
        }
        tvEstimatedPrice.setText(String.format(java.util.Locale.getDefault(), "%.0f DA", estimatedPrice));
        tvEstimatedTime.setText(String.format(java.util.Locale.getDefault(), "~%d min", estimatedTime));

        // AI Vehicle Recommendation display
        currentRecommendedVehicle = res.recommendedVehicle;
        if (tvRecommendedVehicle != null && res.recommendedVehicle != null) {
            String icon = "🛵";
            if ("Voiture".equalsIgnoreCase(res.recommendedVehicle)) icon = "🚗";
            else if ("Camion".equalsIgnoreCase(res.recommendedVehicle)) icon = "🚚";

            if (tvRecommendedVehicleIcon != null) {
                tvRecommendedVehicleIcon.setText("💡 " + icon);
            }
            tvRecommendedVehicle.setText(res.recommendedVehicle + " recommandé");
            if (tvVehicleReason != null && res.vehicleReason != null) {
                tvVehicleReason.setText(res.vehicleReason);
            }

            if (btnApplyRecommendedVehicle != null) {
                boolean alreadySelected = res.recommendedVehicle.equalsIgnoreCase(selectedDeliveryMethod);
                btnApplyRecommendedVehicle.setText(alreadySelected ? "Déjà sélectionné ✓" : "Choisir " + res.recommendedVehicle);
                btnApplyRecommendedVehicle.setEnabled(!alreadySelected);
                btnApplyRecommendedVehicle.setAlpha(alreadySelected ? 0.6f : 1.0f);
            }
        }

        layoutEstimate.setVisibility(View.VISIBLE);

        btnConfirm.setEnabled(true);
        btnConfirm.setAlpha(1.0f);
    }

    private boolean validateOrder() {
        String weightStr = etWeight.getText().toString();

        if (weightStr.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer le poids", Toast.LENGTH_SHORT).show();
            return false;
        }

        double weight;
        try {
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Poids invalide", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedDeliveryMethod.equals("Moto") && weight > 10) {
            Toast.makeText(this, "Le poids dépasse la limite de la moto (10kg)", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedDeliveryMethod.equals("Voiture") && weight > 50) {
            Toast.makeText(this, "Le poids dépasse la limite de la voiture (50kg)", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (selectedDeliveryMethod.equals("Camion") && weight > 500) {
            Toast.makeText(this, "Le poids dépasse la limite du camion (500kg)", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (pickupAddress.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer l'adresse de ramassage", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (deliveryAddress.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer l'adresse de livraison", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (estimatedPrice == 0) {
            Toast.makeText(this, "Veuillez d'abord estimer le prix", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void createOrder() {
        String weightStr = etWeight.getText().toString();
        String instructions = etInstructions.getText().toString();
        double weight = Double.parseDouble(weightStr);
        boolean isUrgent = selectedUrgency.equals("Express");

        String senderUid = session.getSupabaseUid();
        if (senderUid == null) {
            // Old-style local-only account: nothing to sync to the cloud with,
            // so this row gets a locally-generated ID.
            saveLocalMirror(weight, instructions, null);
            Toast.makeText(this, "✅ Commande créée (locale uniquement — reconnectez-vous pour la rendre visible aux livreurs)",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Supabase is the source of truth for order availability — a courier can
        // only ever see orders that exist there, so that write is the one that
        // actually matters. The local write below is just a mirror for screens
        // (History, SenderHome) that still read from SQLite and haven't been
        // migrated yet; it's never the primary record.
        btnConfirm.setEnabled(false);
        // Wilayas come from the picked points, not from the sender's profile. They
        // used to be BOTH set to currentUser.getWilaya(), so a parcel collected in
        // Blida by an Alger-registered user was recorded as an Alger pickup — and
        // DeliveryHomeActivity routes pending orders to couriers by pickup_wilaya,
        // so those orders were shown to the wrong couriers and hidden from the right
        // ones. Falling back to the profile only when reverse geocoding gave nothing.
        String pickupWilaya = wilayaOrFallback(pickupPoint);
        String dropWilaya = wilayaOrFallback(dropPoint);

        // Include photo indicator and dimensions in special instructions if present
        StringBuilder fullInstructions = new StringBuilder();
        if (instructions != null && !instructions.trim().isEmpty()) {
            fullInstructions.append(instructions.trim());
        }
        String dimText = etDimensions.getText() != null ? etDimensions.getText().toString().trim() : "";
        if (!dimText.isEmpty()) {
            if (fullInstructions.length() > 0) fullInstructions.append(" | ");
            fullInstructions.append("Dimensions: ").append(dimText);
        }
        if (selectedPhotoBitmap != null) {
            if (fullInstructions.length() > 0) fullInstructions.append(" | ");
            fullInstructions.append("📷 Photo fournie");
        }
        String combinedInstructions = fullInstructions.toString();

        NewOrderRequest request = new NewOrderRequest(senderUid, estimatedPrice, pickupAddress, deliveryAddress,
                pickupWilaya, dropWilaya,
                pickupPoint.lat, pickupPoint.lng, dropPoint.lat, dropPoint.lng,
                routeKm, estimatedTime,
                selectedPackageType, weight, isUrgent, combinedInstructions);

        orderRepository.create(request, new OrderRepository.OrderCallback() {
            @Override
            public void onSuccess(OrderDto order) {
                // Mirror under the SAME id Supabase assigned, so a later lookup by
                // cloud id (OrderTracking/OrderDetails) resolves to this local row.
                saveLocalMirror(weight, instructions, order.id);
                Toast.makeText(NewDeliveryActivity.this,
                        "✅ Commande créée avec succès!\nID: " + order.id, Toast.LENGTH_LONG).show();
                Intent resultIntent = new Intent();
                resultIntent.putExtra("order_id", order.id);
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onError(String message) {
                // Cloud write failed — save locally so the sender's work isn't lost,
                // but this order stays invisible to couriers until re-created with a
                // working connection (no auto-resync yet). No cloud id exists to
                // adopt, so this row gets a locally-generated one.
                saveLocalMirror(weight, instructions, null);
                Toast.makeText(NewDeliveryActivity.this,
                        "⚠️ Créée hors-ligne seulement, pas encore visible aux livreurs: " + message,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * @param cloudOrderId id assigned by Supabase, or null for orders that only
     *                     exist locally (SQLite then generates its own id).
     */
    private void saveLocalMirror(double weight, String instructions, String cloudOrderId) {
        Order localOrder = new Order(
                cloudOrderId != null ? cloudOrderId : "",
                currentUser.getId(), currentUser.getFullName(), 0, "—", "pending",
                estimatedPrice, estimatedPrice, pickupAddress, deliveryAddress,
                wilayaOrFallback(pickupPoint), wilayaOrFallback(dropPoint), routeKm, estimatedTime,
                null, null, null,
                pickupPoint != null ? pickupPoint.lat : 0.0,
                pickupPoint != null ? pickupPoint.lng : 0.0,
                dropPoint != null ? dropPoint.lat : 0.0,
                dropPoint != null ? dropPoint.lng : 0.0,
                selectedPackageType, weight, instructions);
        db.createOrder(localOrder);
    }

    /**
     * The wilaya of a picked point, or the sender's own as a last resort.
     *
     * <p>Lowercase throughout: the column is already stored that way and the
     * courier's pending-orders filter matches it exactly, so a stray "Alger" would
     * silently match nothing.
     */
    private String wilayaOrFallback(GeoPoint point) {
        if (point != null && point.wilaya != null && !point.wilaya.isEmpty()) {
            return point.wilaya;
        }
        String own = currentUser.getWilaya();
        return own != null ? own.trim().toLowerCase(java.util.Locale.ROOT) : null;
    }
}
