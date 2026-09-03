package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.User;
import com.example.wassilapp.notifications.PushRegistration;
import com.example.wassilapp.remote.AuthRepository;
import com.example.wassilapp.remote.ProfileRepository;
import com.example.wassilapp.remote.SupabaseClient;
import com.example.wassilapp.remote.dto.AuthSession;
import com.example.wassilapp.remote.dto.NewProfileRequest;
import com.example.wassilapp.remote.dto.Profile;
import com.example.wassilapp.utils.PendingProfileStore;
import com.example.wassilapp.utils.SessionManager;

public class RegisterActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private final AuthRepository authRepository = new AuthRepository();
    private final ProfileRepository profileRepository = new ProfileRepository();
    private String role;
    private ProgressBar pbRegister;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        role = getIntent().getStringExtra("role");

        // Administrators are created deliberately in the database, never through
        // self-service signup: an admin can approve withdrawals and read every
        // order and profile.
        if ("admin".equals(role)) {
            Toast.makeText(this,
                    "La création d'un compte administrateur n'est pas autorisée",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize views
        Button btnBack = findViewById(R.id.btnBack);
        EditText etName = findViewById(R.id.etFullName);
        EditText etPhone = findViewById(R.id.etPhone);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        AutoCompleteTextView actvWilaya = findViewById(R.id.actvWilaya);
        EditText etCommune = findViewById(R.id.etCommune);
        LinearLayout llVehicle = findViewById(R.id.llVehicle);
        Spinner spVehicle = findViewById(R.id.spVehicle);
        pbRegister = findViewById(R.id.pbRegister);
        btnRegister = findViewById(R.id.btnRegister);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Wilaya Autocomplete / Dropdown Adapter
        String[] algerianWilayas = new String[]{
                "01 - Adrar", "02 - Chlef", "03 - Laghouat", "04 - Oum El Bouaghi", "05 - Batna",
                "06 - Béjaïa", "07 - Biskra", "08 - Béchar", "09 - Blida", "10 - Bouira",
                "11 - Tamanrasset", "12 - Tébessa", "13 - Tlemcen", "14 - Tiaret", "15 - Tizi Ouzou",
                "16 - Alger", "17 - Djelfa", "18 - Jijel", "19 - Sétif", "20 - Saïda",
                "21 - Skikda", "22 - Sidi Bel Abbès", "23 - Annaba", "24 - Guelma", "25 - Constantine",
                "26 - Médéa", "27 - Mostaganem", "28 - M'Sila", "29 - Mascara", "30 - Ouargla",
                "31 - Oran", "32 - El Bayadh", "33 - Illizi", "34 - Bordj Bou Arréridj", "35 - Boumerdès",
                "36 - El Tarf", "37 - Tindouf", "38 - Tissemsilt", "39 - El Oued", "40 - Khenchela",
                "41 - Souk Ahras", "42 - Tipaza", "43 - Mila", "44 - Aïn Defla", "45 - Naâma",
                "46 - Aïn Témouchent", "47 - Ghardaïa", "48 - Relizane", "49 - El M'Ghair", "50 - El Menia",
                "51 - Ouled Djellal", "52 - Bordj Baji Mokhtar", "53 - Béni Abbès", "54 - Timimoun",
                "55 - Touggourt", "56 - Djanet", "57 - In Salah", "58 - In Guezzam"
        };
        ArrayAdapter<String> wilayaAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, algerianWilayas);
        actvWilaya.setAdapter(wilayaAdapter);
        actvWilaya.setOnClickListener(v -> actvWilaya.showDropDown());
        actvWilaya.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                actvWilaya.showDropDown();
            }
        });

        // Show vehicle selection only for delivery role
        if (role != null && role.equals("delivery")) {
            llVehicle.setVisibility(View.VISIBLE);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.vehicle_types, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spVehicle.setAdapter(adapter);
        }

        btnRegister.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String rawWilaya = actvWilaya.getText().toString().trim();
            // Clean wilaya name if formatted like "16 - Alger"
            String wilaya = cleanWilayaName(rawWilaya);
            String commune = etCommune.getText().toString().trim();
            String vehicle = (role != null && role.equals("delivery")) ?
                    spVehicle.getSelectedItem().toString() : "";

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || wilaya.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs obligatoires", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Le mot de passe doit contenir au moins 6 caractères", Toast.LENGTH_SHORT).show();
                return;
            }

            setLoading(true);
            authRepository.signUp(email, password, new AuthRepository.AuthCallback() {
                @Override
                public void onSuccess(AuthSession authSession) {
                    if (authSession.access_token == null) {
                        // Email confirmation required by Supabase project settings
                        PendingProfileStore.save(RegisterActivity.this, email, name, phone,
                                role, wilaya, commune, vehicle);
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this,
                                "Compte créé ! Confirmez votre email puis connectez-vous.",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    // Direct registration (email confirmation disabled or auto-confirmed)
                    SupabaseClient.setSession(authSession.access_token, authSession.refresh_token);
                    profileRepository.create(
                            new NewProfileRequest(authSession.user.id, name, phone, role, wilaya, commune, vehicle),
                            new ProfileRepository.ProfileCallback() {
                                @Override
                                public void onSuccess(Profile profile) {
                                    // Mirror into local SQLite
                                    int localId = db.upsertUserFromCloud(
                                            profile.id,
                                            profile.full_name,
                                            profile.phone,
                                            profile.role,
                                            profile.vehicle_type,
                                            profile.rating
                                    );

                                    // Save full session and log in immediately
                                    session.saveSupabaseSession(authSession.user.id, email,
                                            authSession.access_token, authSession.refresh_token);
                                    session.createLoginSession(localId, profile.full_name, profile.role);
                                    PushRegistration.syncToken(RegisterActivity.this);

                                    Toast.makeText(RegisterActivity.this,
                                            "Bienvenue sur WASSIL !", Toast.LENGTH_SHORT).show();

                                    // Direct routing to the user's dashboard
                                    Intent intent;
                                    if ("delivery".equals(profile.role)) {
                                        intent = new Intent(RegisterActivity.this, DeliveryHomeActivity.class);
                                    } else {
                                        intent = new Intent(RegisterActivity.this, SenderHomeActivity.class);
                                    }
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }

                                @Override
                                public void onError(String message) {
                                    setLoading(false);
                                    Toast.makeText(RegisterActivity.this,
                                            formatFriendlyError(message), Toast.LENGTH_LONG).show();
                                }
                            });
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Toast.makeText(RegisterActivity.this,
                            formatFriendlyError(message), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setLoading(boolean loading) {
        if (pbRegister != null) {
            pbRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnRegister != null) {
            btnRegister.setEnabled(!loading);
            btnRegister.setText(loading ? "Création du compte..." : "S'inscrire");
        }
    }

    private String cleanWilayaName(String raw) {
        if (raw == null) return "";
        if (raw.contains("-")) {
            String[] parts = raw.split("-");
            return parts[parts.length - 1].trim();
        }
        return raw;
    }

    private String formatFriendlyError(String raw) {
        if (raw == null) return "Une erreur est survenue lors de l'inscription";
        String lower = raw.toLowerCase();
        if (lower.contains("user already registered") || lower.contains("email already exists")) {
            return "Cet email est déjà associé à un compte";
        }
        if (lower.contains("profiles_phone_key") || lower.contains("phone") && lower.contains("unique")) {
            return "Ce numéro de téléphone est déjà utilisé par un autre compte";
        }
        if (lower.contains("password should be at least")) {
            return "Le mot de passe doit comporter au moins 6 caractères";
        }
        if (lower.contains("network") || lower.contains("timeout") || lower.contains("connect")) {
            return "Problème de connexion internet. Veuillez réessayer.";
        }
        return "Erreur d'inscription: " + raw;
    }
}
