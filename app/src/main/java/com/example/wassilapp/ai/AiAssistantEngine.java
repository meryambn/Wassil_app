package com.example.wassilapp.ai;

import android.content.Context;

import com.example.wassilapp.models.AiMessage;
import com.example.wassilapp.utils.SessionManager;

/**
 * Modular orchestrator for the WASSIL AI Assistant.
 * Coordinates intent detection, entity extraction, stateful slot filling,
 * deterministic database tracking, PriceEstimator calculation, and online LLM fallback.
 */
public class AiAssistantEngine {

    public interface EngineCallback {
        void onResponse(AiMessage message);
    }

    private final Context context;
    private final ConversationState state;

    public AiAssistantEngine(Context context) {
        this.context = context.getApplicationContext();
        this.state = new ConversationState();
    }

    public ConversationState getState() {
        return state;
    }

    /**
     * Processes user message asynchronously and returns the structured AI response.
     */
    public void processUserMessage(String userText, EngineCallback callback) {
        if (userText == null || userText.trim().isEmpty()) {
            callback.onResponse(new AiMessage("Que puis-je faire pour vous ?", false));
            return;
        }

        // 1. Entity Extraction into persistent ConversationState
        EntityExtractor.extractEntities(userText, state);

        // 2. Intent Detection
        AiIntent intent = IntentDetector.detectIntent(userText, state);

        // 3. Dispatch to Specialized Handlers

        if (intent == AiIntent.PRICE_ESTIMATE) {
            // Deterministic: PriceEstimator only, no LLM hallucinations
            AiMessage priceMessage = PriceEstimateHandler.handlePriceEstimation(state);
            callback.onResponse(priceMessage);
            return;
        }

        if (intent == AiIntent.TRACK_ORDER) {
            // Deterministic: Database / OrderRepository only
            AiMessage trackMessage = OrderTrackingHandler.handleTracking(context, state);
            callback.onResponse(trackMessage);
            return;
        }

        if (intent == AiIntent.LIST_ORDERS) {
            // Deterministic: Database / OrderRepository only
            AiMessage listMessage = OrderTrackingHandler.handleListOrders(context);
            callback.onResponse(listMessage);
            return;
        }

        // 4. Conversational / FAQ / General Information
        SessionManager session = new SessionManager(context);
        String role = session.getUserRole();

        // Attempt online LLM enrichment; gracefully fall back to local rule-based ResponseBuilder
        OnlineLLMService.queryEdgeFunction(context, userText, role, new OnlineLLMService.LlmCallback() {
            @Override
            public void onSuccess(String reply) {
                if (reply != null && !reply.trim().isEmpty()) {
                    callback.onResponse(new AiMessage(reply.trim(), false));
                } else {
                    onFallback();
                }
            }

            @Override
            public void onFallback() {
                AiMessage localFaqMessage = ResponseBuilder.buildResponse(intent);
                callback.onResponse(localFaqMessage);
            }
        });
    }

    /**
     * Resets conversation slots (e.g., if user taps a fresh quick prompt).
     */
    public void resetConversation() {
        state.reset();
    }
}
