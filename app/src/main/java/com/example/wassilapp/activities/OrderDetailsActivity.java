package com.example.wassilapp.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.utils.SessionManager;

public class OrderDetailsActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private Order currentOrder;
    private String orderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        orderId = getIntent().getStringExtra("order_id");
        currentOrder = db.getOrderById(orderId);

        TextView tvOrderId = findViewById(R.id.tvOrderId);
        TextView tvPrice = findViewById(R.id.tvPrice);
        TextView tvPickupAddress = findViewById(R.id.tvPickupAddress);
        TextView tvDropAddress = findViewById(R.id.tvDropAddress);
        TextView tvPackageType = findViewById(R.id.tvPackageType);
        TextView tvWeight = findViewById(R.id.tvWeight);
        TextView tvUrgency = findViewById(R.id.tvUrgency);
        Button btnGPS = findViewById(R.id.btnGPS);
        Button btnCall = findViewById(R.id.btnCall);
        Button btnWhatsApp = findViewById(R.id.btnWhatsApp);
        Button btnStartDelivery = findViewById(R.id.btnStartDelivery);
        Button btnBack = findViewById(R.id.btnBack);

        if (currentOrder != null) {
            tvOrderId.setText(currentOrder.getOrderId());
            tvPrice.setText(String.format("%.0f DA", currentOrder.getNegotiatedPrice()));
            tvPickupAddress.setText(currentOrder.getPickupAddress());
            tvDropAddress.setText(currentOrder.getDropAddress());
            tvPackageType.setText(currentOrder.getPackageType());
            tvWeight.setText(String.format("%.1f kg", currentOrder.getWeight()));
            tvUrgency.setText("Express");
        }

        btnBack.setOnClickListener(v -> finish());

        btnGPS.setOnClickListener(v -> {
            // Open Google Maps
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + currentOrder.getPickupAddress());
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        });

        btnCall.setOnClickListener(v -> {
            User sender = db.getUserById(currentOrder.getSenderId());
            if (sender != null) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + sender.getPhone()));
                startActivity(callIntent);
            }
        });

        btnWhatsApp.setOnClickListener(v -> {
            User sender = db.getUserById(currentOrder.getSenderId());
            if (sender != null) {
                try {
                    Intent waIntent = new Intent(Intent.ACTION_VIEW);
                    waIntent.setData(Uri.parse("https://wa.me/" + sender.getPhone().replace("+", "")));
                    startActivity(waIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "WhatsApp non installé", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnStartDelivery.setOnClickListener(v -> {
            db.updateOrderStatus(orderId, "en_route");
            Toast.makeText(this, "Livraison commencée!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}