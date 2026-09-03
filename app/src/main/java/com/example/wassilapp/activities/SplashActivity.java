package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_splash);
        } catch (Exception e) {
            // Fallback if layout not found
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText("Wassil Delivery\nLoading...");
            tv.setTextSize(24);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setBackgroundColor(0xFFFF5722);
            tv.setTextColor(0xFFFFFFFF);
            setContentView(tv);
        }

        new Handler().postDelayed(() -> {
            try {
                SessionManager session = new SessionManager(this);
                Intent intent;

                if (session.isLoggedIn()) {
                    String role = session.getUserRole();
                    if (role == null) {
                        intent = new Intent(SplashActivity.this, RoleChoiceActivity.class);
                    } else if (role.equals("sender")) {
                        intent = new Intent(SplashActivity.this, SenderHomeActivity.class);
                    } else if (role.equals("delivery")) {
                        intent = new Intent(SplashActivity.this, DeliveryHomeActivity.class);
                    } else {
                        intent = new Intent(SplashActivity.this, AdminHomeActivity.class);
                    }
                } else {
                    intent = new Intent(SplashActivity.this, RoleChoiceActivity.class);
                }

                startActivity(intent);
                finish();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();

                // Fallback - go to RoleChoiceActivity
                startActivity(new Intent(SplashActivity.this, RoleChoiceActivity.class));
                finish();
            }
        }, 2000);
    }
}