package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.notifications.PushRegistration;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.User;
import com.example.wassilapp.utils.SessionManager;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private User currentUser;

    private TextView tvName, tvRole, tvPhone, tvEmail, tvBalance, tvVehicle, tvRating, tvWilaya;
    private Button btnLogout, btnEditProfile, btnAddBalance;
    private LinearLayout layoutVehicle, layoutRating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        String suid = session.getSupabaseUid();
        if (suid != null && !suid.isEmpty()) {
            currentUser = db.getUserBySupabaseUid(suid);
        }
        if (currentUser == null) {
            currentUser = db.getUserById(session.getUserId());
        }

        // Fallback: if user is still null or has outdated role, construct from session so role is always accurate
        if (currentUser == null || (session.getUserRole() != null && !session.getUserRole().equals(currentUser.getRole()))) {
            int localId = db.upsertUserFromCloud(
                    suid != null ? suid : "sender-cloud",
                    session.getUserName() != null ? session.getUserName() : "Utilisateur",
                    "0555000000",
                    session.getUserRole() != null ? session.getUserRole() : "sender",
                    "",
                    5.0
            );
            currentUser = db.getUserById(localId);
        }

        initUI();
        loadUserData();
        setupButtons();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Bottom navigation
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            if (currentUser.getRole().equals("sender")) {
                startActivity(new Intent(this, SenderHomeActivity.class));
            } else {
                startActivity(new Intent(this, DeliveryHomeActivity.class));
            }
            finish();
        });

        findViewById(R.id.btnHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            finish();
        });
    }

    private void initUI() {
        tvName = findViewById(R.id.tvProfileName);
        tvRole = findViewById(R.id.tvProfileRole);
        tvPhone = findViewById(R.id.tvProfilePhone);
        tvEmail = findViewById(R.id.tvProfileEmail);
        tvBalance = findViewById(R.id.tvProfileBalance);
        tvVehicle = findViewById(R.id.tvProfileVehicle);
        tvRating = findViewById(R.id.tvProfileRating);
        tvWilaya = findViewById(R.id.tvProfileWilaya);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnAddBalance = findViewById(R.id.btnAddBalance);
        layoutVehicle = findViewById(R.id.layoutVehicle);
        layoutRating = findViewById(R.id.layoutRating);
    }

    private void loadUserData() {
        tvName.setText(currentUser.getFullName());
        tvPhone.setText(currentUser.getPhone());
        String email = currentUser.getEmail();
        tvEmail.setText((email != null && !email.isEmpty()) ? email : "Non renseigné");
        tvBalance.setText(String.format("%.0f DA", currentUser.getBalance()));
        tvWilaya.setText(currentUser.getWilaya() + " - " + currentUser.getCommune());

        if (currentUser.getRole().equals("delivery")) {
            tvRole.setText("Livreur • ⭐ " + currentUser.getRating());
            layoutVehicle.setVisibility(View.VISIBLE);
            layoutRating.setVisibility(View.VISIBLE);
            tvVehicle.setText(currentUser.getVehicleType());
            tvRating.setText(String.valueOf(currentUser.getRating()));
            btnAddBalance.setVisibility(View.GONE);
        } else if (currentUser.getRole().equals("sender")) {
            tvRole.setText("Expéditeur");
            layoutVehicle.setVisibility(View.GONE);
            layoutRating.setVisibility(View.GONE);
            btnAddBalance.setVisibility(View.VISIBLE);
        } else {
            tvRole.setText("Administrateur");
            layoutVehicle.setVisibility(View.GONE);
            layoutRating.setVisibility(View.GONE);
            btnAddBalance.setVisibility(View.GONE);
        }
    }

    private void setupButtons() {
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Déconnexion")
                    .setMessage("Voulez-vous vraiment vous déconnecter?")
                    .setPositiveButton("Oui", (dialog, which) -> {
                        // Retire the device token first: the delete is authorised by
                        // the current user's JWT, so clearing the session before it
                        // would strand the row and keep sending this user's messages
                        // to a phone somebody else is now using. Logout proceeds
                        // regardless of whether the retirement succeeded.
                        PushRegistration.retireToken(this, () -> {
                            session.logout();
                            startActivity(new Intent(this, RoleChoiceActivity.class));
                            finishAffinity();
                        });
                    })
                    .setNegativeButton("Non", null)
                    .show();
        });

        // Only couriers are verified; a sender has nothing to submit.
        View btnKyc = findViewById(R.id.btnKycVerification);
        if (currentUser != null && "delivery".equals(currentUser.getRole())) {
            btnKyc.setVisibility(View.VISIBLE);
            btnKyc.setOnClickListener(v -> startActivity(new Intent(this, KycActivity.class)));
        } else {
            btnKyc.setVisibility(View.GONE);
        }

        btnEditProfile.setOnClickListener(v -> {
            showEditProfileDialog();
        });

        btnAddBalance.setOnClickListener(v -> {
            showAddBalanceDialog();
        });
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);

        EditText etName = view.findViewById(R.id.etEditName);
        EditText etPhone = view.findViewById(R.id.etEditPhone);
        EditText etEmail = view.findViewById(R.id.etEditEmail);
        EditText etWilaya = view.findViewById(R.id.etEditWilaya);
        EditText etCommune = view.findViewById(R.id.etEditCommune);

        etName.setText(currentUser.getFullName());
        etPhone.setText(currentUser.getPhone());
        etEmail.setText(currentUser.getEmail());
        etWilaya.setText(currentUser.getWilaya());
        etCommune.setText(currentUser.getCommune());

        builder.setTitle("Modifier le profil")
                .setView(view)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String newPhone = etPhone.getText().toString().trim();
                    String newEmail = etEmail.getText().toString().trim();
                    String newWilaya = etWilaya.getText().toString().trim();
                    String newCommune = etCommune.getText().toString().trim();

                    if (newName.isEmpty() || newPhone.isEmpty()) {
                        Toast.makeText(this, "Nom et téléphone sont obligatoires", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean updated = db.updateUserProfile(currentUser.getId(), newName, newPhone, newEmail, newWilaya, newCommune);

                    if (updated) {
                        currentUser = db.getUserById(currentUser.getId());
                        loadUserData();
                        Toast.makeText(this, "Profil mis à jour!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Erreur lors de la mise à jour", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showAddBalanceDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_balance, null);
        EditText etAmount = view.findViewById(R.id.etTopUpAmount);
        TextView tvError = view.findViewById(R.id.tvTopUpError);
        Button btnPack1000 = view.findViewById(R.id.btnPack1000);
        Button btnPack2000 = view.findViewById(R.id.btnPack2000);
        Button btnPack5000 = view.findViewById(R.id.btnPack5000);

        btnPack1000.setOnClickListener(v -> {
            etAmount.setText("1000");
            tvError.setVisibility(View.GONE);
        });
        btnPack2000.setOnClickListener(v -> {
            etAmount.setText("2000");
            tvError.setVisibility(View.GONE);
        });
        btnPack5000.setOnClickListener(v -> {
            etAmount.setText("5000");
            tvError.setVisibility(View.GONE);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Recharger mon solde")
                .setView(view)
                .setPositiveButton("Recharger", null)
                .setNegativeButton("Annuler", null)
                .create();

        dialog.show();

        // Custom listener after show() to prevent auto-dismissing on invalid validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                tvError.setText("Veuillez saisir un montant");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                tvError.setText("Format de montant invalide");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            final double MIN_TOPUP = 500.0;
            final double MAX_TOPUP = 50000.0;
            if (amount < MIN_TOPUP) {
                tvError.setText(String.format(Locale.getDefault(),
                        "Montant minimum de recharge : %.0f DA", MIN_TOPUP));
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (amount > MAX_TOPUP) {
                tvError.setText(String.format(Locale.getDefault(),
                        "Montant maximum par recharge : %.0f DA", MAX_TOPUP));
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            double newBalance = currentUser.getBalance() + amount;
            db.updateUserBalance(currentUser.getId(), newBalance);
            currentUser = db.getUserById(currentUser.getId());
            tvBalance.setText(String.format(Locale.getDefault(), "%.0f DA", currentUser.getBalance()));
            Toast.makeText(this, String.format(Locale.getDefault(),
                    "%.0f DA ajoutés à votre solde WASSIL", amount), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    private void navigateTo(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }
}