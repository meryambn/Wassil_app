package com.example.wassilapp.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wassilapp.R;
import com.example.wassilapp.remote.KycRepository;
import com.example.wassilapp.remote.dto.KycDocumentDto;
import com.example.wassilapp.utils.SessionManager;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a courier submits their identity documents.
 *
 * <p>Nothing here decides whether the courier is verified. The screen shows what the
 * server says and offers to upload; approval happens only through an admin calling
 * review_kyc_document, and profiles.kyc_status is written by a trigger. A courier can
 * neither approve their own document nor set their own status — both were measured
 * as 403 against a real token.
 */
public class KycActivity extends AppCompatActivity {

    private static final int REQ_PICK_CIN = 7001;
    private static final int REQ_PICK_PERMIS = 7002;
    private static final int REQ_PICK_CARTE_GRISE = 7003;

    /** Long edge after downscaling. Enough to read an ID card, small enough to send. */
    private static final int MAX_DIMENSION = 1600;
    private static final int JPEG_QUALITY = 80;

    private final KycRepository repository = new KycRepository();
    private final Map<String, String> statusByType = new HashMap<>();

    private SessionManager session;
    private TextView tvOverall, tvStatusCin, tvStatusPermis, tvStatusCarteGrise;
    private Button btnCin, btnPermis, btnCarteGrise;
    private View progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kyc);

        session = new SessionManager(this);
        tvOverall = findViewById(R.id.tvKycStatus);
        tvStatusCin = findViewById(R.id.tvStatusCin);
        tvStatusPermis = findViewById(R.id.tvStatusPermis);
        tvStatusCarteGrise = findViewById(R.id.tvStatusCarteGrise);
        btnCin = findViewById(R.id.btnUploadCin);
        btnPermis = findViewById(R.id.btnUploadPermis);
        btnCarteGrise = findViewById(R.id.btnUploadCarteGrise);
        progress = findViewById(R.id.progressKyc);

        findViewById(R.id.btnBackKyc).setOnClickListener(v -> finish());
        btnCin.setOnClickListener(v -> pickImage(REQ_PICK_CIN));
        btnPermis.setOnClickListener(v -> pickImage(REQ_PICK_PERMIS));
        btnCarteGrise.setOnClickListener(v -> pickImage(REQ_PICK_CARTE_GRISE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDocuments();
    }

    private void pickImage(int requestCode) {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(pick, "Choisir le document"), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String docType = requestCode == REQ_PICK_CIN ? "cin"
                : requestCode == REQ_PICK_PERMIS ? "permis"
                : requestCode == REQ_PICK_CARTE_GRISE ? "carte_grise" : null;
        if (docType == null) {
            return;
        }
        upload(docType, data.getData());
    }

    private void upload(String docType, Uri uri) {
        Bitmap bitmap = decodeScaled(uri);
        if (bitmap == null) {
            Toast.makeText(this, "Impossible de lire l'image", Toast.LENGTH_LONG).show();
            return;
        }
        byte[] bytes = compress(bitmap);
        if (bytes == null) {
            bitmap.recycle();
            Toast.makeText(this, "Impossible de lire l'image", Toast.LENGTH_LONG).show();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        // Read the document before sending it. On-device and offline, so nothing about
        // the ID leaves the phone for this step, and a failure costs nothing: the
        // upload proceeds with no text and the admin reviews exactly as before.
        recogniseText(bitmap, ocrText -> {
            bitmap.recycle();
            send(docType, bytes, ocrText);
        });
    }

    /**
     * Runs on-device text recognition.
     *
     * <p>Always calls back, including on failure. OCR is a convenience for whoever
     * reviews the document; letting it block a submission would trade a real feature
     * for a cosmetic one.
     */
    private void recogniseText(Bitmap bitmap, java.util.function.Consumer<String> then) {
        try {
            TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(result -> then.accept(result.getText()))
                    .addOnFailureListener(e -> then.accept(null));
        } catch (Throwable t) {
            then.accept(null);
        }
    }

    private void send(String docType, byte[] bytes, String ocrText) {
        repository.submit(session.getSupabaseUid(), docType, bytes, "image/jpeg", ocrText,
                new KycRepository.SubmitCallback() {
                    @Override
                    public void onSubmitted(KycDocumentDto document) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        Toast.makeText(KycActivity.this,
                                "Document envoyé — en attente de vérification",
                                Toast.LENGTH_LONG).show();
                        loadDocuments();
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        progress.setVisibility(View.GONE);
                        setButtonsEnabled(true);
                        Toast.makeText(KycActivity.this,
                                "Envoi impossible. " + message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Decodes, downscales and re-encodes the picked image.
     *
     * <p>A modern phone camera produces several megabytes per shot, and the bucket
     * rejects anything over five. inSampleSize decodes at a reduced size rather than
     * loading the full bitmap first, which also avoids an OutOfMemoryError on a large
     * photo — decoding before measuring is the usual way this crashes.
     */
    private Bitmap decodeScaled(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }

            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / sample > MAX_DIMENSION) {
                sample *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            return bitmap;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Kept separate from decoding so the same bitmap can be read by OCR first. */
    private byte[] compress(Bitmap bitmap) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            return out.toByteArray();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void loadDocuments() {
        String uid = session.getSupabaseUid();
        if (uid == null) {
            tvOverall.setText("Reconnectez-vous pour gérer vos documents.");
            return;
        }
        repository.getMine(uid, new KycRepository.ListCallback() {
            @Override
            public void onSuccess(List<KycDocumentDto> documents) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                setButtonsEnabled(true);

                // Newest first from the query, so the first of each type seen is the
                // one that counts — matching how the server decides kyc_status.
                statusByType.clear();
                for (KycDocumentDto d : documents) {
                    if (d.doc_type != null && !statusByType.containsKey(d.doc_type)) {
                        statusByType.put(d.doc_type, d.status);
                    }
                }
                render();
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) {
                    progress.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvOverall.setText("Impossible de charger vos documents.");
                }
            }
        });
    }

    private void render() {
        tvStatusCin.setText(describe(statusByType.get("cin")));
        tvStatusPermis.setText(describe(statusByType.get("permis")));
        tvStatusCarteGrise.setText(describe(statusByType.get("carte_grise")));

        boolean allApproved = true;
        boolean anyRejected = false;
        for (String type : KycRepository.REQUIRED_TYPES) {
            String s = statusByType.get(type);
            if (!"approved".equals(s)) allApproved = false;
            if ("rejected".equals(s)) anyRejected = true;
        }

        if (allApproved) {
            tvOverall.setText("✅ Compte vérifié");
        } else if (anyRejected) {
            tvOverall.setText("❌ Un document a été refusé — merci d'en envoyer un nouveau");
        } else if (!statusByType.isEmpty()) {
            tvOverall.setText("⏳ Vérification en cours");
        } else {
            tvOverall.setText("Envoyez vos documents pour vérifier votre compte");
        }

        // A rejected document must stay replaceable, so only a pending one locks the
        // button — re-sending while a review is underway would just queue duplicates.
        btnCin.setEnabled(!"pending".equals(statusByType.get("cin")));
        btnPermis.setEnabled(!"pending".equals(statusByType.get("permis")));
        btnCarteGrise.setEnabled(!"pending".equals(statusByType.get("carte_grise")));
    }

    private String describe(String status) {
        if (status == null) return "Aucun document envoyé";
        switch (status) {
            case "approved": return "✅ Validé";
            case "rejected": return "❌ Refusé — envoyez un nouveau document";
            case "pending":  return "⏳ En cours de vérification";
            default: return status;
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        btnCin.setEnabled(enabled);
        btnPermis.setEnabled(enabled);
        btnCarteGrise.setEnabled(enabled);
    }
}
