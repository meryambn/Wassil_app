package com.example.wassilapp.activities;


import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.adapters.DeliveryRequestAdapter;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.utils.SessionManager;

public class NegotiatePriceActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private Order currentOrder;
    private String orderId;
    private double originalPrice;

    private TextView tvOrderId, tvOriginalPrice;
    private EditText etProposedPrice;
    private Button btnSubmit, btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_negotiate_price);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        orderId = getIntent().getStringExtra("order_id");
        originalPrice = getIntent().getDoubleExtra("original_price", 0);

        initUI();
        loadData();
        setupButtons();
    }

    private void initUI() {
        tvOrderId = findViewById(R.id.tvNegotiateOrderId);
        tvOriginalPrice = findViewById(R.id.tvOriginalPrice);
        etProposedPrice = findViewById(R.id.etProposedPrice);
        btnSubmit = findViewById(R.id.btnSubmitNegotiation);
        btnCancel = findViewById(R.id.btnCancelNegotiation);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void loadData() {
        currentOrder = db.getOrderById(orderId);
        tvOrderId.setText(orderId);
        tvOriginalPrice.setText(String.format("%.0f DA", originalPrice));
        etProposedPrice.setText(String.valueOf((int)originalPrice));
    }

    private void setupButtons() {
        btnSubmit.setOnClickListener(v -> {
            String proposedStr = etProposedPrice.getText().toString();
            if (proposedStr.isEmpty()) {
                Toast.makeText(this, "Entrez un prix", Toast.LENGTH_SHORT).show();
                return;
            }

            double proposedPrice = Double.parseDouble(proposedStr);

            // Update order with negotiated price
            db.assignDeliveryToOrder(orderId, session.getUserId(),
                    session.getUserName(), proposedPrice);

            Toast.makeText(this, "Prix négocié: " + proposedPrice + " DA", Toast.LENGTH_LONG).show();
            finish();
        });

        btnCancel.setOnClickListener(v -> finish());
    }
}