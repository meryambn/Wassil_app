package com.example.wassilapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.adapters.AiMessageAdapter;
import com.example.wassilapp.ai.AiAssistantEngine;
import com.example.wassilapp.models.AiMessage;
import com.example.wassilapp.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Conversational AI Assistant for WASSIL platform.
 * Supports parcel tracking, dynamic price estimation, and FAQ guidance.
 */
public class AiAssistantActivity extends AppCompatActivity implements AiMessageAdapter.OnActionClickListener {

    private RecyclerView recyclerChat;
    private EditText etInput;
    private ImageButton btnSend;
    private AiMessageAdapter adapter;
    private final List<AiMessage> messages = new ArrayList<>();
    private AiAssistantEngine engine;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        session = new SessionManager(this);
        engine = new AiAssistantEngine(this);

        initViews();
        setupRecyclerView();
        setupSuggestionChips();
        setupListeners();
        displayWelcomeMessage();
    }

    private void initViews() {
        recyclerChat = findViewById(R.id.recyclerAiChat);
        etInput = findViewById(R.id.etAiInput);
        btnSend = findViewById(R.id.btnAiSend);

        findViewById(R.id.btnBackAi).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new AiMessageAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);
        recyclerChat.setAdapter(adapter);
    }

    private void setupSuggestionChips() {
        findViewById(R.id.chipTrack).setOnClickListener(v -> sendQuery("Où est mon colis ?"));
        findViewById(R.id.chipEstimate).setOnClickListener(v -> sendQuery("Combien coûte une livraison ?"));
        findViewById(R.id.chipOrders).setOnClickListener(v -> sendQuery("Mes commandes"));
        findViewById(R.id.chipCourier).setOnClickListener(v -> sendQuery("Comment devenir livreur ?"));
        findViewById(R.id.chipKyc).setOnClickListener(v -> sendQuery("Comment valider mon compte KYC ?"));
        findViewById(R.id.chipPayment).setOnClickListener(v -> sendQuery("Quels sont les modes de paiement ?"));
        findViewById(R.id.chipWithdrawals).setOnClickListener(v -> sendQuery("Comment retirer mes gains ?"));
        findViewById(R.id.chipProhibited).setOnClickListener(v -> sendQuery("Quels sont les articles interdits ?"));
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> submitInput());

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput();
                return true;
            }
            return false;
        });
    }

    private void displayWelcomeMessage() {
        String userName = session.getUserName();
        String greeting = (userName != null && !userName.trim().isEmpty())
                ? "Bonjour " + userName + " ! 👋"
                : "Bonjour ! 👋";

        String welcomeText = greeting + "\nJe suis l'assistant intelligent **WASSIL**.\n\n" +
                "Comment puis-je vous aider aujourd'hui ? Vous pouvez me demander de suivre un colis, " +
                "d'estimer un tarif de livraison, ou de vous renseigner sur le fonctionnement de l'application.";

        AiMessage welcome = new AiMessage(welcomeText, false);
        messages.add(welcome);
        adapter.notifyItemInserted(messages.size() - 1);
    }

    private void submitInput() {
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (text.isEmpty()) {
            return;
        }
        etInput.setText("");
        sendQuery(text);
    }

    private void sendQuery(String text) {
        // 1. Add user message
        AiMessage userMsg = new AiMessage(text, true);
        messages.add(userMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.smoothScrollToPosition(messages.size() - 1);

        // 2. Process with AI Engine
        engine.processUserMessage(text, response -> {
            if (isFinishing() || isDestroyed()) return;

            messages.add(response);
            adapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.smoothScrollToPosition(messages.size() - 1);
        });
    }

    @Override
    public void onActionCardClicked(AiMessage message) {
        if (message == null || message.getActionType() == null) {
            return;
        }

        switch (message.getActionType()) {
            case AiMessage.ACTION_TRACK_ORDER:
                if (message.getActionOrderId() != null) {
                    Intent trackIntent = new Intent(this, OrderTrackingActivity.class);
                    trackIntent.putExtra("order_id", message.getActionOrderId());
                    trackIntent.putExtra("user_type", session.getUserRole() != null ? session.getUserRole() : "sender");
                    startActivity(trackIntent);
                } else {
                    Toast.makeText(this, "Numéro de commande manquant", Toast.LENGTH_SHORT).show();
                }
                break;

            case AiMessage.ACTION_CREATE_DELIVERY:
                Intent createIntent = new Intent(this, NewDeliveryActivity.class);
                if (message.getPrefillPickup() != null) {
                    createIntent.putExtra("extra_pickup_wilaya", message.getPrefillPickup());
                }
                if (message.getPrefillDrop() != null) {
                    createIntent.putExtra("extra_drop_wilaya", message.getPrefillDrop());
                }
                if (message.getPrefillWeight() > 0) {
                    createIntent.putExtra("extra_weight", message.getPrefillWeight());
                }
                if (message.getPrefillPackageType() != null) {
                    createIntent.putExtra("extra_package_type", message.getPrefillPackageType());
                }
                if (message.getPrefillDimensions() != null) {
                    createIntent.putExtra("extra_dimensions", message.getPrefillDimensions());
                }
                createIntent.putExtra("extra_urgent", message.isPrefillUrgent());
                startActivity(createIntent);
                break;

            case AiMessage.ACTION_OPEN_KYC:
                startActivity(new Intent(this, KycActivity.class));
                break;

            case AiMessage.ACTION_OPEN_WITHDRAWALS:
                startActivity(new Intent(this, CourierWithdrawalsActivity.class));
                break;

            default:
                break;
        }
    }
}
