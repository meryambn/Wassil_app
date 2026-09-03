package com.example.wassilapp.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.wassilapp.R;
import com.example.wassilapp.geo.GeoRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;

/**
 * Picks a delivery point by dragging the map under a fixed centre pin.
 *
 * <p><b>Why a pin and not an address search.</b> Typing an address and geocoding it
 * assumes reliable street addressing. Much of Algeria does not have that — a real
 * delivery address is often a landmark, and the numbering that does exist is
 * frequently missing from map data. A dropped pin is exact regardless of whether
 * anyone ever named the street. The text field underneath carries the part a
 * geocoder could never know: which gate, which floor.
 *
 * <p>The screen this replaces was a bare EditText in a dialog, which produced a
 * string and no coordinates at all — which is why every order in the database has
 * NULL latitude and a distance of exactly 5.0 km.
 */
public class AddressPickerActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_WILAYA = "wilaya";

    private static final int LOCATION_PERMISSION_REQUEST = 3001;

    /** Place du 1er Mai, Algiers — a sane starting view before we know better. */
    private static final double DEFAULT_LAT = 36.7538;
    private static final double DEFAULT_LNG = 3.0588;
    private static final double DEFAULT_ZOOM = 14.0;

    /**
     * The map keeps moving for a moment after a finger lifts, and every intermediate
     * position would otherwise cost a geocoding request. Waiting for things to settle
     * turns a drag into one lookup instead of dozens.
     */
    private static final long SETTLE_DELAY_MS = 500L;

    private final GeoRepository geo = new GeoRepository();
    private final Handler handler = new Handler();

    private MapView mapView;
    private EditText etResolvedAddress;
    private EditText etDetail;
    private Button btnConfirm;

    // Search components
    private EditText etSearch;
    private TextView btnSearchClear;
    private Button btnSearchGo;
    private View cardSearchResults;
    private android.widget.LinearLayout layoutSearchResults;

    private boolean styleReady = false;
    private String resolvedAddress;
    private String resolvedWilaya;
    private Runnable pendingResolve;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_picker);

        mapView = findViewById(R.id.mapPicker);
        etResolvedAddress = findViewById(R.id.etResolvedAddress);
        etDetail = findViewById(R.id.etAddressDetail);
        btnConfirm = findViewById(R.id.btnConfirmAddress);

        etSearch = findViewById(R.id.etSearchAddress);
        btnSearchClear = findViewById(R.id.btnSearchClear);
        btnSearchGo = findViewById(R.id.btnSearchGo);
        cardSearchResults = findViewById(R.id.cardSearchResults);
        layoutSearchResults = findViewById(R.id.layoutSearchResults);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        TextView tvTitle = findViewById(R.id.tvPickerTitle);
        tvTitle.setText(title != null ? title : "Choisir l'adresse");

        findViewById(R.id.btnBackPicker).setOnClickListener(v -> finish());
        findViewById(R.id.btnMyLocationCard).setOnClickListener(v -> centreOnMyLocation());
        findViewById(R.id.btnMyLocation).setOnClickListener(v -> centreOnMyLocation());
        btnConfirm.setOnClickListener(v -> confirm());

        setupSearch();

        // Enable confirm as soon as user types an address manually
        etResolvedAddress.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.toString().trim().length() > 2) {
                    btnConfirm.setEnabled(true);
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        double startLat = getIntent().getDoubleExtra(EXTRA_LAT, DEFAULT_LAT);
        double startLng = getIntent().getDoubleExtra(EXTRA_LNG, DEFAULT_LNG);

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            styleReady = true;
            moveCamera(startLat, startLng);
            scheduleResolve();
        });

        mapView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                scheduleResolve();
            }
            return false;
        });
    }

    private void setupSearch() {
        btnSearchGo.setOnClickListener(v -> performSearch());

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                performSearch();
                return true;
            }
            return false;
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSearchClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            cardSearchResults.setVisibility(View.GONE);
            layoutSearchResults.removeAllViews();
        });
    }

    private void performSearch() {
        String query = etSearch.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer une adresse ou un lieu", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        double proxLat = DEFAULT_LAT;
        double proxLng = DEFAULT_LNG;
        if (styleReady) {
            Point centre = mapView.getMapboxMap().getCameraState().getCenter();
            if (centre != null) {
                proxLat = centre.latitude();
                proxLng = centre.longitude();
            }
        }

        btnSearchGo.setEnabled(false);
        geo.searchAddress(query, proxLat, proxLng, results -> {
            btnSearchGo.setEnabled(true);
            if (isFinishing() || isDestroyed()) return;

            layoutSearchResults.removeAllViews();
            if (results.isEmpty()) {
                cardSearchResults.setVisibility(View.VISIBLE);
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("Aucun résultat trouvé pour \"" + query + "\"");
                tvEmpty.setPadding(32, 24, 32, 24);
                tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
                layoutSearchResults.addView(tvEmpty);
                return;
            }

            cardSearchResults.setVisibility(View.VISIBLE);
            int count = Math.min(results.size(), 5);
            for (int i = 0; i < count; i++) {
                GeoRepository.SearchResult res = results.get(i);
                TextView tvItem = new TextView(this);
                tvItem.setText("📍 " + res.placeName);
                tvItem.setTextSize(14f);
                tvItem.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvItem.setPadding(32, 24, 32, 24);
                tvItem.setBackgroundResource(R.drawable.bg_bubble_received);
                tvItem.setClickable(true);
                tvItem.setFocusable(true);

                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(16, 4, 16, 4);
                tvItem.setLayoutParams(params);

                tvItem.setOnClickListener(itemV -> {
                    cardSearchResults.setVisibility(View.GONE);
                    moveCamera(res.lat, res.lng);
                    resolvedAddress = res.placeName;
                    resolvedWilaya = res.wilaya;
                    etResolvedAddress.setText(res.placeName);
                    btnConfirm.setEnabled(true);
                });
                layoutSearchResults.addView(tvItem);
            }
        });
    }

    private void moveCamera(double lat, double lng) {
        if (!styleReady) {
            return;
        }
        mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                .center(Point.fromLngLat(lng, lat))
                .zoom(DEFAULT_ZOOM)
                .build());
    }

    /** Debounced: a new gesture cancels the lookup queued by the previous one. */
    private void scheduleResolve() {
        if (pendingResolve != null) {
            handler.removeCallbacks(pendingResolve);
        }
        etResolvedAddress.setHint("Recherche de l'adresse…");

        pendingResolve = this::resolveCentre;
        handler.postDelayed(pendingResolve, SETTLE_DELAY_MS);
    }

    private void resolveCentre() {
        if (!styleReady || isFinishing() || isDestroyed()) {
            return;
        }
        Point centre = mapView.getMapboxMap().getCameraState().getCenter();
        if (centre == null) {
            return;
        }

        geo.describe(centre.latitude(), centre.longitude(), (address, wilaya) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            resolvedAddress = address;
            resolvedWilaya = wilaya;

            // Update text without clearing if the user was typing their own
            if (address != null) {
                etResolvedAddress.setText(address);
            } else {
                etResolvedAddress.setText(String.format(java.util.Locale.US, "%.5f, %.5f",
                        centre.latitude(), centre.longitude()));
            }
            btnConfirm.setEnabled(true);
        });
    }

    private void confirm() {
        Point centre = mapView.getMapboxMap().getCameraState().getCenter();
        if (centre == null) {
            Toast.makeText(this, "Carte non prête", Toast.LENGTH_SHORT).show();
            return;
        }

        String userEnteredAddress = etResolvedAddress.getText().toString().trim();
        String detail = etDetail.getText().toString().trim();

        String base = !userEnteredAddress.isEmpty() ? userEnteredAddress
                : (resolvedAddress != null ? resolvedAddress
                : String.format(java.util.Locale.US, "%.5f, %.5f", centre.latitude(), centre.longitude()));

        String full = detail.isEmpty() ? base : detail + " — " + base;

        Intent result = new Intent();
        result.putExtra(EXTRA_LAT, centre.latitude());
        result.putExtra(EXTRA_LNG, centre.longitude());
        result.putExtra(EXTRA_ADDRESS, full);
        result.putExtra(EXTRA_WILAYA, resolvedWilaya);
        setResult(RESULT_OK, result);
        finish();
    }

    private void centreOnMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
        try {
            client.getLastLocation().addOnSuccessListener(location -> {
                if (location == null) {
                    Toast.makeText(this, "Position indisponible", Toast.LENGTH_SHORT).show();
                    return;
                }
                moveCamera(location.getLatitude(), location.getLongitude());
                scheduleResolve();
            });
        } catch (SecurityException e) {
            // Permission revoked between the check above and the call.
            Toast.makeText(this, "Position indisponible", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            centreOnMyLocation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingResolve != null) {
            handler.removeCallbacks(pendingResolve);
        }
    }
}
