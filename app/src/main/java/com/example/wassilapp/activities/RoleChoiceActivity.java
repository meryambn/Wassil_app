package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;

public class RoleChoiceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_choice);

        LinearLayout llSender = findViewById(R.id.llSender);
        LinearLayout llDelivery = findViewById(R.id.llDelivery);
        LinearLayout llAdmin = findViewById(R.id.llAdmin);

        llSender.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("role", "sender");
            startActivity(intent);
        });

        llDelivery.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("role", "delivery");
            startActivity(intent);
        });

        llAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("role", "admin");
            startActivity(intent);
        });
    }
}