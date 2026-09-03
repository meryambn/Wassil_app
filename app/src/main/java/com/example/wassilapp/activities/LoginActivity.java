package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.notifications.PushRegistration;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.AuthRepository;
import com.example.wassilapp.remote.ProfileRepository;
import com.example.wassilapp.remote.dto.AuthSession;
import com.example.wassilapp.remote.dto.NewProfileRequest;
import com.example.wassilapp.remote.dto.Profile;
import com.example.wassilapp.utils.PendingProfileStore;
import com.example.wassilapp.utils.SessionManager;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private SessionManager session;
    private final AuthRepository authRepository = new AuthRepository();
    private final ProfileRepository profileRepository = new ProfileRepository();
    private String role;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        role = getIntent().getStringExtra("role");

        TextView tvTitle = findViewById(R.id.tvLoginTitle);
        tvTitle.setText("Connexion — " +
                (role.equals("sender") ? "Expéditeur" : role.equals("delivery") ? "Livreur" : "Admin"));

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Entrez votre email et mot de passe", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            authRepository.signIn(email, password, new AuthRepository.AuthCallback() {
                @Override
                public void onSuccess(AuthSession authSession) {
                    session.saveSupabaseSession(authSession.user.id, email,
                            authSession.access_token, authSession.refresh_token);
                    resolveProfile(authSession.user.id, email);
                }

                @Override
                public void onError(String message) {
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Connexion échouée: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            intent.putExtra("role", role);
            startActivity(intent);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void resolveProfile(String uid, String email) {
        profileRepository.getById(uid, new ProfileRepository.ProfileLookupCallback() {
            @Override
            public void onFound(Profile profile) {
                enterAsRole(profile);
            }

            @Override
            public void onNotFound() {
                // First login after confirming email: finish the deferred profile creation.
                JSONObject pending = PendingProfileStore.consume(LoginActivity.this, email);
                if (pending == null) {
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this,
                            "Profil introuvable. Réessayez de vous inscrire.", Toast.LENGTH_LONG).show();
                    return;
                }
                NewProfileRequest req = new NewProfileRequest(uid,
                        pending.optString("full_name"), pending.optString("phone"),
                        pending.optString("role"), pending.optString("wilaya"),
                        pending.optString("commune"), pending.optString("vehicle_type"));
                profileRepository.create(req, new ProfileRepository.ProfileCallback() {
                    @Override
                    public void onSuccess(Profile profile) {
                        enterAsRole(profile);
                    }

                    @Override
                    public void onError(String message) {
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Erreur profil: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Erreur: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void enterAsRole(Profile profile) {
        if (!profile.role.equals(role)) {
            btnLogin.setEnabled(true);
            Toast.makeText(this, "Ce compte n'a pas le rôle sélectionné", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mirror into the legacy local SQLite session so screens still on
        // DatabaseHelper (not yet migrated to Supabase) keep working.
        User localUser = db.getUserByPhone(profile.phone);
        int localId;
        if (localUser != null) {
            localId = localUser.getId();
        } else {
            User newLocal = new User(0, profile.full_name, profile.phone, session.getEmail(),
                    profile.role, profile.balance, profile.vehicle_type, profile.rating,
                    profile.wilaya, profile.commune, profile.is_active, profile.profile_image_url);
            localId = (int) db.addUser(newLocal);
        }
        // Bind the local row to the cloud identity so sync can map UUID <-> local int id.
        db.linkUserToSupabase(localId, profile.id);
        session.createLoginSession(localId, profile.full_name, profile.role);

        // After the session exists, never before: the registration write is
        // authorised by this user's JWT, and onNewToken alone would not fire here
        // because the device token itself has not changed -- only who is using it.
        PushRegistration.syncToken(this);

        Intent intent;
        switch (role) {
            case "sender":
                intent = new Intent(this, SenderHomeActivity.class);
                break;
            case "delivery":
                intent = new Intent(this, DeliveryHomeActivity.class);
                break;
            default:
                intent = new Intent(this, AdminHomeActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
