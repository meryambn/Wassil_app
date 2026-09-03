package com.example.wassilapp.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.User;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import java.util.List;

public class MapboxActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private MapView mapView;
    private DatabaseHelper db;
    private LinearLayout llActiveDeliveries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapbox);

        db = new DatabaseHelper(this);
        llActiveDeliveries = findViewById(R.id.llActiveDeliveries);
        mapView = findViewById(R.id.mapView);

        // Back button
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Load active deliveries list
        loadActiveDeliveries();

        // Initialize map
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            // Map is ready
            enableLocation();
        });
    }

    private void loadActiveDeliveries() {
        List<User> activeDeliveries = db.getNearbyDeliveries("Alger");
        llActiveDeliveries.removeAllViews();

        if (activeDeliveries != null && !activeDeliveries.isEmpty()) {
            for (User delivery : activeDeliveries) {
                TextView tvDelivery = new TextView(this);
                tvDelivery.setText(String.format("🟢 %s - %s (⭐ %.1f)",
                        delivery.getFullName(),
                        delivery.getVehicleType(),
                        delivery.getRating()));
                tvDelivery.setPadding(16, 12, 16, 12);
                tvDelivery.setTextSize(14);
                tvDelivery.setTextColor(getColor(android.R.color.black));
                llActiveDeliveries.addView(tvDelivery);
            }
        } else {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Aucun livreur actif");
            tvEmpty.setPadding(16, 32, 16, 32);
            tvEmpty.setTextColor(getColor(android.R.color.darker_gray));
            llActiveDeliveries.addView(tvEmpty);
        }
    }

    private void enableLocation() {
        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        Toast.makeText(this, "Localisation activée", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission accordée", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
    }

    @Override
    public void onLowMemory() {  // Changed from protected to public
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}