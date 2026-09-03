package com.example.wassilapp.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.adapters.OrderAdapter;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.geo.AdminMapOverlay;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.KycRepository;
import com.example.wassilapp.remote.LocationRepository;
import com.example.wassilapp.remote.OrderRepository;
import com.example.wassilapp.remote.OrderSyncManager;
import com.example.wassilapp.remote.PlatformSettingsRepository;
import com.example.wassilapp.remote.ProfileRepository;
import com.example.wassilapp.remote.WithdrawalRepository;
import com.example.wassilapp.remote.dto.CourierLocationDto;
import com.example.wassilapp.remote.dto.KycDocumentDto;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.remote.dto.Profile;
import com.example.wassilapp.remote.dto.WithdrawalDto;
import com.example.wassilapp.utils.SessionManager;
import com.google.android.material.chip.ChipGroup;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxStyleManager;
import com.mapbox.maps.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AdminHomeActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private DatabaseHelper db;
    private SessionManager session;
    private OrderSyncManager syncManager;
    private OrderRepository orderRepository;
    private ProfileRepository profileRepository;
    private KycRepository kycRepository;
    private WithdrawalRepository withdrawalRepository;
    private LocationRepository locationRepository;
    private PlatformSettingsRepository platformSettingsRepository;
    private double currentCommissionRate = 15.0;

    private MapView mapView;
    private MapboxStyleManager mapStyleManager;
    private boolean isMapStyleReady = false;

    // Cloud in-memory collections
    private final List<Profile> cloudCouriers = new ArrayList<>();
    private final List<OrderDto> allCloudOrders = new ArrayList<>();
    private final List<Order> displayedOrders = new ArrayList<>();
    private final List<com.example.wassilapp.remote.dto.CourierLocationDto> activeCourierLocations = new ArrayList<>();

    // UI elements
    private TextView tvTotalOrders;
    private TextView tvTotalRevenue;
    private TextView tvActiveDeliveries;
    private TextView tvOngoingDeliveries;
    private TextView tvMapStatus;
    private TextView tvOrdersEmpty;
    private TextView tvAdminCommissionRateBadge;
    private TextView tvPlatformCommissionTotal;
    private TextView tvCourierNetTotal;
    private Button btnManageKyc;
    private Button btnManageWithdrawals;
    private Button btnManageCommissions;
    private Button btnToggleOrdersLimit;
    private LinearLayout llDeliveries;
    private RecyclerView recyclerOrders;
    private OrderAdapter ordersAdapter;
    private EditText etOrderSearch;
    private ChipGroup chipGroupFilters;

    private static final int INITIAL_ORDER_LIMIT = 5;
    private boolean isExpandedOrders = false;
    private String currentStatusFilter = "all";
    private String currentSearchText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_home);

        initServices();
        initViews();
        setupOrderRecycler();
        setupFiltersAndSearch();
        setupActionButtons();
        setupMap();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllDashboardData();
    }

    private void initServices() {
        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        syncManager = new OrderSyncManager(this);
        orderRepository = new OrderRepository();
        profileRepository = new ProfileRepository();
        kycRepository = new KycRepository();
        withdrawalRepository = new WithdrawalRepository();
        locationRepository = new LocationRepository();
        platformSettingsRepository = new PlatformSettingsRepository();
        currentCommissionRate = db.getCommissionPercentage();
    }

    private void initViews() {
        tvTotalOrders = findViewById(R.id.tvTotalOrders);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvActiveDeliveries = findViewById(R.id.tvActiveDeliveries);
        tvOngoingDeliveries = findViewById(R.id.tvOngoingDeliveries);
        tvMapStatus = findViewById(R.id.tvMapStatus);
        tvOrdersEmpty = findViewById(R.id.tvOrdersEmpty);
        tvAdminCommissionRateBadge = findViewById(R.id.tvAdminCommissionRateBadge);
        tvPlatformCommissionTotal = findViewById(R.id.tvPlatformCommissionTotal);
        tvCourierNetTotal = findViewById(R.id.tvCourierNetTotal);

        btnManageKyc = findViewById(R.id.btnManageKyc);
        btnManageWithdrawals = findViewById(R.id.btnManageWithdrawals);
        btnManageCommissions = findViewById(R.id.btnManageCommissions);
        btnToggleOrdersLimit = findViewById(R.id.btnToggleOrdersLimit);
        llDeliveries = findViewById(R.id.llActiveDeliveries);
        recyclerOrders = findViewById(R.id.recyclerRecentOrders);
        etOrderSearch = findViewById(R.id.etOrderSearch);
        chipGroupFilters = findViewById(R.id.chipGroupOrderFilters);
        mapView = findViewById(R.id.mapView);

        btnToggleOrdersLimit.setOnClickListener(v -> {
            isExpandedOrders = !isExpandedOrders;
            filterAndDisplayOrders();
        });

        findViewById(R.id.btnRefreshDashboard).setOnClickListener(v -> {
            Toast.makeText(this, "Actualisation des données...", Toast.LENGTH_SHORT).show();
            loadAllDashboardData();
        });
    }

    private void setupOrderRecycler() {
        recyclerOrders.setLayoutManager(new LinearLayoutManager(this));
        ordersAdapter = new OrderAdapter(displayedOrders, this, "admin");
        ordersAdapter.setOnOrderClickListener(order -> {
            Intent intent = new Intent(this, OrderTrackingActivity.class);
            intent.putExtra("order_id", order.getOrderId());
            intent.putExtra("user_type", "admin");
            startActivity(intent);
        });
        recyclerOrders.setAdapter(ordersAdapter);
    }

    private void setupFiltersAndSearch() {
        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipFilterPending) {
                currentStatusFilter = "pending";
            } else if (checkedId == R.id.chipFilterOngoing) {
                currentStatusFilter = "ongoing";
            } else if (checkedId == R.id.chipFilterDelivered) {
                currentStatusFilter = "livre";
            } else if (checkedId == R.id.chipFilterCancelled) {
                currentStatusFilter = "annule";
            } else {
                currentStatusFilter = "all";
            }
            filterAndDisplayOrders();
        });

        etOrderSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchText = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                filterAndDisplayOrders();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupActionButtons() {
        btnManageWithdrawals.setOnClickListener(v ->
                startActivity(new Intent(this, AdminWithdrawalsActivity.class)));

        btnManageKyc.setOnClickListener(v ->
                startActivity(new Intent(this, AdminKycActivity.class)));

        if (btnManageCommissions != null) {
            btnManageCommissions.setOnClickListener(v -> showCommissionDialog());
        }

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new SessionManager(this).logout();
            finishAffinity();
            startActivity(new Intent(this, RoleChoiceActivity.class));
        });
    }

    private void setupMap() {
        if (mapView != null) {
            mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
                mapStyleManager = style;
                isMapStyleReady = true;
                updateMapMarkers();
                checkLocationPermission();
            });
        }
    }

    /**
     * Loads authoritative platform data from Supabase across all admin domains.
     */
    private void loadAllDashboardData() {
        loadOrdersFromCloud();
        loadCouriersFromCloud();
        loadLiveCourierLocations();
        loadKycPendingCount();
        loadWithdrawalsPendingCount();
        loadCommissionSettings();
    }

    private void loadLiveCourierLocations() {
        locationRepository.getAllLocations(new LocationRepository.AllLocationsCallback() {
            @Override
            public void onSuccess(List<com.example.wassilapp.remote.dto.CourierLocationDto> locations) {
                if (isFinishing() || isDestroyed()) return;
                activeCourierLocations.clear();
                if (locations != null) {
                    activeCourierLocations.addAll(locations);
                }
                updateMapMarkers();
            }

            @Override
            public void onError(String message) {
                // Non-critical, markers update without crash
            }
        });
    }

    // 1. ORDERS & REVENUE
    private void loadOrdersFromCloud() {
        orderRepository.getAllOrders(new OrderRepository.OrderListCallback() {
            @Override
            public void onSuccess(List<OrderDto> orders) {
                if (isFinishing() || isDestroyed()) return;

                allCloudOrders.clear();
                allCloudOrders.addAll(orders);

                // Sync to local SQLite cache
                User admin = db.getUserById(session.getUserId());
                if (admin != null) {
                    syncManager.syncAllOrders(session.getSupabaseUid(), admin.getId(),
                            admin.getFullName(), new OrderSyncManager.SyncCallback() {
                                @Override
                                public void onSynced(int count) {}

                                @Override
                                public void onError(String message) {}
                            });
                }

                computeOrderKPIs();
                filterAndDisplayOrders();
                updateMapMarkers();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                // Fallback to SQLite cache if offline
                List<Order> localOrders = db.getAllOrders();
                displayedOrders.clear();
                displayedOrders.addAll(localOrders);
                ordersAdapter.notifyDataSetChanged();

                tvTotalOrders.setText(String.valueOf(db.getTotalOrders()));
                tvTotalRevenue.setText(String.format(Locale.FRENCH, "%.0f DA", db.getTotalRevenue()));
                tvOrdersEmpty.setVisibility(displayedOrders.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void computeOrderKPIs() {
        int total = allCloudOrders.size();
        double totalDeliveredRevenue = 0.0;
        int ongoing = 0;

        List<String> ongoingStatuses = Arrays.asList(
                "prise_en_charge", "vers_depart", "colis_recupere", "en_route"
        );

        for (OrderDto o : allCloudOrders) {
            if ("livre".equals(o.status)) {
                if (o.negotiated_price != null && o.negotiated_price > 0) {
                    totalDeliveredRevenue += o.negotiated_price;
                } else {
                    totalDeliveredRevenue += o.asking_price;
                }
            } else if (ongoingStatuses.contains(o.status)) {
                ongoing++;
            }
        }

        tvTotalOrders.setText(String.valueOf(total));
        tvTotalRevenue.setText(String.format(Locale.FRENCH, "%.0f DA", totalDeliveredRevenue));
        tvOngoingDeliveries.setText(String.valueOf(ongoing));

        double platformCut = totalDeliveredRevenue * (currentCommissionRate / 100.0);
        double courierCut = totalDeliveredRevenue - platformCut;

        if (tvAdminCommissionRateBadge != null) {
            tvAdminCommissionRateBadge.setText(String.format(Locale.FRENCH, "Taux : %.0f%%", currentCommissionRate));
        }
        if (tvPlatformCommissionTotal != null) {
            tvPlatformCommissionTotal.setText(String.format(Locale.FRENCH, "%.0f DA", platformCut));
        }
        if (tvCourierNetTotal != null) {
            tvCourierNetTotal.setText(String.format(Locale.FRENCH, "%.0f DA", courierCut));
        }
        if (btnManageCommissions != null) {
            btnManageCommissions.setText(String.format(Locale.FRENCH, "💼 Gestion des commissions (%.0f%%)", currentCommissionRate));
        }
    }

    private void filterAndDisplayOrders() {
        displayedOrders.clear();
        List<Order> matchingOrders = new ArrayList<>();
        List<String> ongoingStatuses = Arrays.asList(
                "prise_en_charge", "vers_depart", "colis_recupere", "en_route"
        );

        for (OrderDto dto : allCloudOrders) {
            // Status check
            boolean matchesStatus;
            if ("all".equals(currentStatusFilter)) {
                matchesStatus = true;
            } else if ("ongoing".equals(currentStatusFilter)) {
                matchesStatus = ongoingStatuses.contains(dto.status);
            } else {
                matchesStatus = currentStatusFilter.equals(dto.status);
            }

            if (!matchesStatus) continue;

            // Search query check
            if (!currentSearchText.isEmpty()) {
                String idStr = dto.id != null ? dto.id.toLowerCase(Locale.ROOT) : "";
                String senderStr = (dto.sender != null && dto.sender.full_name != null) ? dto.sender.full_name.toLowerCase(Locale.ROOT) : "";
                String deliveryStr = (dto.delivery != null && dto.delivery.full_name != null) ? dto.delivery.full_name.toLowerCase(Locale.ROOT) : "";
                String pickupStr = dto.pickup_address != null ? dto.pickup_address.toLowerCase(Locale.ROOT) : "";
                String dropStr = dto.drop_address != null ? dto.drop_address.toLowerCase(Locale.ROOT) : "";
                String wilayaP = dto.pickup_wilaya != null ? dto.pickup_wilaya.toLowerCase(Locale.ROOT) : "";
                String wilayaD = dto.drop_wilaya != null ? dto.drop_wilaya.toLowerCase(Locale.ROOT) : "";

                boolean matchesSearch = idStr.contains(currentSearchText)
                        || senderStr.contains(currentSearchText)
                        || deliveryStr.contains(currentSearchText)
                        || pickupStr.contains(currentSearchText)
                        || dropStr.contains(currentSearchText)
                        || wilayaP.contains(currentSearchText)
                        || wilayaD.contains(currentSearchText);

                if (!matchesSearch) continue;
            }

            // Convert matching OrderDto to Order for the adapter
            String senderName = dto.sender != null ? dto.sender.full_name : "";
            String deliveryName = dto.delivery != null ? dto.delivery.full_name : "—";

            Order o = new Order(
                    dto.id, 0, senderName, 0, deliveryName, dto.status,
                    dto.asking_price, dto.negotiated_price != null ? dto.negotiated_price : 0,
                    dto.pickup_address, dto.drop_address, dto.pickup_wilaya, dto.drop_wilaya,
                    dto.distance_km != null ? dto.distance_km : 0,
                    dto.estimated_minutes != null ? dto.estimated_minutes : 0,
                    dto.created_at, dto.picked_up_at, dto.delivered_at,
                    dto.pickup_lat != null ? dto.pickup_lat : 0,
                    dto.pickup_lng != null ? dto.pickup_lng : 0,
                    dto.drop_lat != null ? dto.drop_lat : 0,
                    dto.drop_lng != null ? dto.drop_lng : 0,
                    dto.package_type, dto.weight_kg != null ? dto.weight_kg : 0,
                    dto.special_instructions
            );
            matchingOrders.add(o);
        }

        int totalMatching = matchingOrders.size();

        if (isExpandedOrders || totalMatching <= INITIAL_ORDER_LIMIT) {
            displayedOrders.addAll(matchingOrders);
        } else {
            displayedOrders.addAll(matchingOrders.subList(0, INITIAL_ORDER_LIMIT));
        }

        ordersAdapter.notifyDataSetChanged();
        tvOrdersEmpty.setVisibility(displayedOrders.isEmpty() ? View.VISIBLE : View.GONE);

        // Update Load More / Show Less Button state
        if (totalMatching > INITIAL_ORDER_LIMIT) {
            btnToggleOrdersLimit.setVisibility(View.VISIBLE);
            if (isExpandedOrders) {
                btnToggleOrdersLimit.setText(String.format(Locale.FRENCH, "▲ Réduire la liste (Afficher %d commandes)", INITIAL_ORDER_LIMIT));
            } else {
                int remaining = totalMatching - INITIAL_ORDER_LIMIT;
                btnToggleOrdersLimit.setText(String.format(Locale.FRENCH, "▼ Afficher plus de commandes (+%d restantes)", remaining));
            }
        } else {
            btnToggleOrdersLimit.setVisibility(View.GONE);
        }
    }

    // 2. COURIERS (CLOUD)
    private void loadCouriersFromCloud() {
        profileRepository.getCouriers(new ProfileRepository.CouriersCallback() {
            @Override
            public void onSuccess(List<Profile> couriers) {
                if (isFinishing() || isDestroyed()) return;

                cloudCouriers.clear();
                cloudCouriers.addAll(couriers);

                renderCouriersList();
                updateMapMarkers();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                // Fallback to SQLite cache
                List<User> localDeliveries = db.getNearbyDeliveries("Alger");
                tvActiveDeliveries.setText(String.valueOf(db.getActiveDeliveries()));
            }
        });
    }

    private void renderCouriersList() {
        llDeliveries.removeAllViews();

        int activeCount = 0;
        for (Profile p : cloudCouriers) {
            if (p.is_active) activeCount++;
        }
        tvActiveDeliveries.setText(String.valueOf(activeCount > 0 ? activeCount : cloudCouriers.size()));

        if (cloudCouriers.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Aucun livreur inscrit sur la plateforme");
            tvEmpty.setPadding(16, 32, 16, 32);
            tvEmpty.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            tvEmpty.setTextSize(14);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            llDeliveries.addView(tvEmpty);
            return;
        }

        for (int i = 0; i < cloudCouriers.size(); i++) {
            Profile courier = cloudCouriers.get(i);
            View deliveryCard = getLayoutInflater().inflate(R.layout.item_delivery_person, null);

            TextView tvInitial = deliveryCard.findViewById(R.id.tvInitial);
            TextView tvName = deliveryCard.findViewById(R.id.tvDeliveryName);
            TextView tvInfo = deliveryCard.findViewById(R.id.tvDeliveryInfo);
            TextView tvRating = deliveryCard.findViewById(R.id.tvDeliveryRating);

            String firstLetter = courier.full_name != null && !courier.full_name.isEmpty()
                    ? courier.full_name.substring(0, 1).toUpperCase(Locale.ROOT) : "L";
            tvInitial.setText(firstLetter);
            tvName.setText(courier.full_name != null ? courier.full_name : "Livreur");

            String vehicle = courier.vehicle_type != null && !courier.vehicle_type.isEmpty() ? courier.vehicle_type : "Véhicule non spécifié";
            String kycStat = "approved".equals(courier.kyc_status) ? " • KYC ✅" : ("pending".equals(courier.kyc_status) ? " • KYC ⏳" : "");
            tvInfo.setText(vehicle + kycStat);

            tvRating.setText(String.format(Locale.FRENCH, "⭐ %.1f", courier.rating));

            // Tapping a courier opens the details dialog
            deliveryCard.setOnClickListener(v -> showCourierDetailsDialog(courier));

            llDeliveries.addView(deliveryCard);

            if (i < cloudCouriers.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFEEEEEE);
                llDeliveries.addView(divider);
            }
        }
    }

    private void showCourierDetailsDialog(Profile courier) {
        View view = getLayoutInflater().inflate(R.layout.dialog_courier_details, null);

        TextView tvInitial = view.findViewById(R.id.tvDialogInitial);
        TextView tvName = view.findViewById(R.id.tvDialogCourierName);
        TextView tvVehicle = view.findViewById(R.id.tvDialogVehicle);
        TextView tvRating = view.findViewById(R.id.tvDialogRating);
        TextView tvActive = view.findViewById(R.id.tvDialogActiveStatus);
        TextView tvKyc = view.findViewById(R.id.tvDialogKycBadge);
        TextView tvWilaya = view.findViewById(R.id.tvDialogWilaya);
        TextView tvPhone = view.findViewById(R.id.tvDialogPhone);
        Button btnClose = view.findViewById(R.id.btnDialogClose);
        Button btnCall = view.findViewById(R.id.btnDialogCall);

        String firstLetter = courier.full_name != null && !courier.full_name.isEmpty()
                ? courier.full_name.substring(0, 1).toUpperCase(Locale.ROOT) : "L";
        tvInitial.setText(firstLetter);
        tvName.setText(courier.full_name != null ? courier.full_name : "Livreur");
        tvVehicle.setText(courier.vehicle_type != null ? courier.vehicle_type : "Non spécifié");
        tvRating.setText(String.format(Locale.FRENCH, " • ⭐ %.1f (%d avis)", courier.rating, courier.rating_count));

        if (courier.is_active) {
            tvActive.setText("Actif");
            tvActive.setTextColor(0xFF2E7D32);
            tvActive.setBackgroundColor(0xFFE8F5E9);
        } else {
            tvActive.setText("Inactif");
            tvActive.setTextColor(0xFF757575);
            tvActive.setBackgroundColor(0xFFF5F5F5);
        }

        if ("approved".equals(courier.kyc_status)) {
            tvKyc.setText("Validé (Approuvé)");
            tvKyc.setTextColor(0xFF00897B);
        } else if ("pending".equals(courier.kyc_status)) {
            tvKyc.setText("En attente d'examen");
            tvKyc.setTextColor(0xFFF57C0D);
        } else {
            tvKyc.setText("Non soumis / Rejeté");
            tvKyc.setTextColor(0xFFD32F2F);
        }

        tvWilaya.setText(courier.wilaya != null ? courier.wilaya : "Algérie");
        tvPhone.setText(courier.phone != null ? courier.phone : "Non renseigné");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        if (courier.phone != null && !courier.phone.isEmpty()) {
            btnCall.setEnabled(true);
            btnCall.setOnClickListener(v -> {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + courier.phone));
                startActivity(callIntent);
            });
        } else {
            btnCall.setEnabled(false);
            btnCall.setAlpha(0.5f);
        }

        dialog.show();
    }

    // 3. PENDING KYC BADGE
    private void loadKycPendingCount() {
        kycRepository.getPendingQueue(new KycRepository.ListCallback() {
            @Override
            public void onSuccess(List<KycDocumentDto> documents) {
                if (isFinishing() || isDestroyed()) return;
                int count = documents.size();
                if (count > 0) {
                    btnManageKyc.setText(String.format(Locale.FRENCH, "Vérifications d'identité (%d en attente)", count));
                    btnManageKyc.setBackgroundTintList(ContextCompat.getColorStateList(AdminHomeActivity.this, R.color.primary));
                } else {
                    btnManageKyc.setText("Vérifications d'identité (0 en attente)");
                }
            }

            @Override
            public void onError(String message) {
                // Silent fallback
            }
        });
    }

    // 4. PENDING WITHDRAWALS BADGE
    private void loadWithdrawalsPendingCount() {
        withdrawalRepository.getAllWithdrawals(new WithdrawalRepository.HistoryCallback() {
            @Override
            public void onSuccess(List<WithdrawalDto> withdrawals) {
                if (isFinishing() || isDestroyed()) return;
                int pendingCount = 0;
                for (WithdrawalDto w : withdrawals) {
                    if ("pending".equals(w.status)) {
                        pendingCount++;
                    }
                }

                if (pendingCount > 0) {
                    btnManageWithdrawals.setText(String.format(Locale.FRENCH, "Gérer les retraits (%d en attente)", pendingCount));
                } else {
                    btnManageWithdrawals.setText("Gérer les retraits (0 en attente)");
                }
            }

            @Override
            public void onError(String message) {
                // Silent fallback
            }
        });
    }

    // 5. COMMISSION MANAGEMENT
    private void loadCommissionSettings() {
        platformSettingsRepository.getCommissionRate(new PlatformSettingsRepository.RateCallback() {
            @Override
            public void onRateLoaded(double percentage) {
                if (isFinishing() || isDestroyed()) return;
                currentCommissionRate = percentage;
                db.setCommissionPercentage(percentage);
                computeOrderKPIs();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                currentCommissionRate = db.getCommissionPercentage();
                computeOrderKPIs();
            }
        });
    }

    private void showCommissionDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_commission_settings, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        TextView tvCurrentRate = view.findViewById(R.id.tvDialogCurrentRate);
        EditText etRate = view.findViewById(R.id.etCommissionRate);
        TextView tvSimPlatform = view.findViewById(R.id.tvSimPlatform);
        TextView tvSimCourier = view.findViewById(R.id.tvSimCourier);

        tvCurrentRate.setText(String.format(Locale.FRENCH, "%.0f %%", currentCommissionRate));
        etRate.setText(String.format(Locale.US, "%.0f", currentCommissionRate));

        Runnable updateSim = () -> {
            String text = etRate.getText() != null ? etRate.getText().toString().trim() : "";
            try {
                double r = Double.parseDouble(text);
                double platform = 1000.0 * (r / 100.0);
                double courier = 1000.0 - platform;
                tvSimPlatform.setText(String.format(Locale.FRENCH, "Plateforme : %.0f DA", platform));
                tvSimCourier.setText(String.format(Locale.FRENCH, "Livreur : %.0f DA", courier));
            } catch (Exception e) {
                tvSimPlatform.setText("Plateforme : — DA");
                tvSimCourier.setText("Livreur : — DA");
            }
        };

        updateSim.run();

        etRate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSim.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.btnPreset5).setOnClickListener(v -> { etRate.setText("5"); etRate.setSelection(1); });
        view.findViewById(R.id.btnPreset10).setOnClickListener(v -> { etRate.setText("10"); etRate.setSelection(2); });
        view.findViewById(R.id.btnPreset15).setOnClickListener(v -> { etRate.setText("15"); etRate.setSelection(2); });
        view.findViewById(R.id.btnPreset20).setOnClickListener(v -> { etRate.setText("20"); etRate.setSelection(2); });
        view.findViewById(R.id.btnPreset25).setOnClickListener(v -> { etRate.setText("25"); etRate.setSelection(2); });

        view.findViewById(R.id.btnCancelCommission).setOnClickListener(v -> dialog.dismiss());

        Button btnSave = view.findViewById(R.id.btnSaveCommission);
        btnSave.setOnClickListener(v -> {
            String text = etRate.getText() != null ? etRate.getText().toString().trim() : "";
            double newRate;
            try {
                newRate = Double.parseDouble(text);
            } catch (Exception e) {
                Toast.makeText(this, "Veuillez entrer un pourcentage valide", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newRate < 0 || newRate > 100) {
                Toast.makeText(this, "Le taux doit être entre 0% et 100%", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSave.setEnabled(false);
            btnSave.setText("Enregistrement...");

            platformSettingsRepository.setCommissionRate(newRate, new PlatformSettingsRepository.SetRateCallback() {
                @Override
                public void onRateSaved(double percentage) {
                    if (isFinishing() || isDestroyed()) return;
                    currentCommissionRate = percentage;
                    db.setCommissionPercentage(percentage);
                    computeOrderKPIs();
                    dialog.dismiss();
                    Toast.makeText(AdminHomeActivity.this,
                            String.format(Locale.FRENCH, "Taux de commission mis à jour : %.0f%%", percentage),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    btnSave.setEnabled(true);
                    btnSave.setText("Enregistrer");
                    Toast.makeText(AdminHomeActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    // 5. MAP RENDERING & MARKERS
    private void updateMapMarkers() {
        if (!isMapStyleReady || mapStyleManager == null || mapView == null) return;

        List<AdminMapOverlay.MapMarker> courierPins = new ArrayList<>();
        List<AdminMapOverlay.MapMarker> pickupPins = new ArrayList<>();
        List<AdminMapOverlay.MapMarker> dropPins = new ArrayList<>();
        List<AdminMapOverlay.MapMarker> allFramePoints = new ArrayList<>();

        // 1. Order Pins (for active / ongoing / pending orders)
        List<String> activeStatuses = Arrays.asList(
                "pending", "prise_en_charge", "vers_depart", "colis_recupere", "en_route"
        );

        for (OrderDto o : allCloudOrders) {
            if (activeStatuses.contains(o.status)) {
                if (o.pickup_lat != null && o.pickup_lng != null
                        && (Math.abs(o.pickup_lat) > 0.01 || Math.abs(o.pickup_lng) > 0.01)) {
                    AdminMapOverlay.MapMarker p = new AdminMapOverlay.MapMarker(
                            o.pickup_lat, o.pickup_lng, AdminMapOverlay.COLOR_ORDER_PICKUP, "Départ: " + o.pickup_address
                    );
                    pickupPins.add(p);
                    allFramePoints.add(p);
                }

                if (o.drop_lat != null && o.drop_lng != null
                        && (Math.abs(o.drop_lat) > 0.01 || Math.abs(o.drop_lng) > 0.01)) {
                    AdminMapOverlay.MapMarker d = new AdminMapOverlay.MapMarker(
                            o.drop_lat, o.drop_lng, AdminMapOverlay.COLOR_ORDER_DROP, "Arrivée: " + o.drop_address
                    );
                    dropPins.add(d);
                    allFramePoints.add(d);
                }
            }
        }

        // 2. Courier Pins (from live GPS pings in courier_locations table)
        for (com.example.wassilapp.remote.dto.CourierLocationDto loc : activeCourierLocations) {
            // Check if coordinates are valid (within Algeria bounds, not 0,0 or US emulator defaults)
            if (loc.lat != 0 && loc.lng != 0 && loc.lat >= 18.0 && loc.lat <= 38.0 && loc.lng >= -9.0 && loc.lng <= 13.0) {
                int color = AdminMapOverlay.COLOR_COURIER_CAR;
                // Match vehicle type from registered couriers if available
                if (cloudCouriers != null) {
                    for (Profile c : cloudCouriers) {
                        if (c.id != null && c.id.equals(loc.courier_id)) {
                            if ("moto".equalsIgnoreCase(c.vehicle_type)) {
                                color = AdminMapOverlay.COLOR_COURIER_MOTO;
                            } else if ("camion".equalsIgnoreCase(c.vehicle_type)) {
                                color = AdminMapOverlay.COLOR_COURIER_TRUCK;
                            }
                            break;
                        }
                    }
                }
                AdminMapOverlay.MapMarker cMarker = new AdminMapOverlay.MapMarker(
                        loc.lat, loc.lng, color, "Livreur en mission (Cmd: " + loc.order_id + ")"
                );
                courierPins.add(cMarker);
                allFramePoints.add(cMarker);
            }
        }

        // Render markers on Mapbox
        AdminMapOverlay.renderMarkers(mapStyleManager, courierPins, pickupPins, dropPins);

        // Frame camera safely
        float density = getResources().getDisplayMetrics().density;
        AdminMapOverlay.framePoints(
                mapView.getMapboxMap(), allFramePoints,
                mapView.getWidth() / density, mapView.getHeight() / density
        );

        int totalPins = pickupPins.size() + dropPins.size() + courierPins.size();
        tvMapStatus.setText(totalPins > 0
                ? totalPins + " point(s) actif(s) affiché(s)"
                : "Carte centrée • 0 livraison active");
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}