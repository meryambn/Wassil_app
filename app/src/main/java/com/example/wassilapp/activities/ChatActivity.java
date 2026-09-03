package com.example.wassilapp.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wassilapp.R;
import com.example.wassilapp.adapters.MessageAdapter;
import com.example.wassilapp.remote.MessageRepository;
import com.example.wassilapp.remote.dto.MessageDto;
import com.example.wassilapp.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chat between the sender and the courier of one order — the "discussion" the cahier
 * des charges lists next to the offer system as a core feature.
 *
 * <p><b>Why polling and not websockets.</b> Supabase does offer Realtime, but this
 * project has no table in the supabase_realtime publication and the app talks to the
 * backend purely over REST (Retrofit/PostgREST). Adding true realtime means speaking
 * the Phoenix channel protocol over a raw websocket — a new dependency, a new
 * reconnection/backoff problem, and a second code path that can silently stop
 * working. Polling every few seconds reuses machinery that already works everywhere
 * else in the app. It is an honest trade: messages appear within a few seconds
 * rather than instantly.
 *
 * <p>The poll is <i>incremental</i>. Each request asks only for messages newer than
 * the newest one already on screen, using {@code created_at=gt.<cursor>}. The cursor
 * is always a timestamp the server produced, never the phone's clock, so two devices
 * whose clocks disagree still converge on the same conversation.
 *
 * <p>Security is entirely in the database: messages_select limits rows to the order's
 * sender and courier, and messages_insert additionally requires
 * {@code sender_id = auth.uid()}, so no client can read another conversation or post
 * a message under someone else's name.
 */
public class ChatActivity extends AppCompatActivity {

    /** Intent extras. The calling screen owns the order, so it decides these. */
    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_NAME = "counterparty_name";
    public static final String EXTRA_PHONE = "counterparty_phone";
    public static final String EXTRA_CHAT_OPEN = "chat_open";

    /** Faster than the 5s tracking loop: a conversation feels broken if it lags. */
    private static final long POLL_INTERVAL_MS = 3_000L;

    private final MessageRepository repository = new MessageRepository();
    private final List<MessageDto> messages = new ArrayList<>();

    /**
     * Guards against showing the same message twice. The cursor uses a strict
     * "greater than", so a duplicate would need two rows sharing a created_at to the
     * microsecond — vanishingly unlikely, but a resend after a timeout could also
     * produce one, and a duplicated message is the kind of bug users never forgive.
     */
    private final Set<String> seenIds = new HashSet<>();

    private final Handler handler = new Handler();
    private Runnable pollRunnable;

    private MessageAdapter adapter;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private EditText etMessage;
    private View btnSend, composer, progress;
    private TextView tvEmpty, tvClosed;

    private String orderId;
    private String myUid;
    private String counterpartyPhone;
    private boolean chatOpen;
    /** created_at of the newest message we hold — the polling cursor. */
    private String lastCreatedAt;
    private boolean sending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        counterpartyPhone = getIntent().getStringExtra(EXTRA_PHONE);
        chatOpen = getIntent().getBooleanExtra(EXTRA_CHAT_OPEN, false);
        myUid = new SessionManager(this).getSupabaseUid();

        recycler = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSendMessage);
        composer = findViewById(R.id.llComposer);
        progress = findViewById(R.id.progressChat);
        tvEmpty = findViewById(R.id.tvChatEmpty);
        tvClosed = findViewById(R.id.tvChatClosed);

        TextView tvName = findViewById(R.id.tvChatCounterparty);
        TextView tvOrder = findViewById(R.id.tvChatOrderId);
        tvName.setText(TextUtils.isEmpty(name) ? "Discussion" : name);
        tvOrder.setText(orderId != null ? orderId : "");

        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());
        findViewById(R.id.btnCallCounterparty).setOnClickListener(v -> callCounterparty());
        btnSend.setOnClickListener(v -> sendMessage());

        layoutManager = new LinearLayoutManager(this);
        // Anchor short conversations to the bottom, the way every chat app behaves.
        layoutManager.setStackFromEnd(true);
        recycler.setLayoutManager(layoutManager);
        adapter = new MessageAdapter(messages, this, myUid);
        recycler.setAdapter(adapter);

        applyOpenState();

        if (orderId == null || myUid == null) {
            // Without an order id there is nothing to load; without a uid every
            // request would go out as the anon key and RLS would return an empty
            // list, which looks exactly like "no messages" — say so instead.
            showClosed("Discussion indisponible. Reconnectez-vous puis réessayez.");
            return;
        }

        loadInitial();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Polling only runs while the screen is actually in front of the user. A chat
        // left open in the background would otherwise keep waking the radio every
        // 3 seconds for a conversation nobody is reading.
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }

    // ---- loading --------------------------------------------------------------

    private void loadInitial() {
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        repository.getMessages(orderId, null, new MessageRepository.MessageListCallback() {
            @Override
            public void onSuccess(List<MessageDto> loaded) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                appendNew(loaded, true);
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.setVisibility(View.GONE);
                // The first load failing is worth telling the user about; later poll
                // failures are not, because the screen still shows valid content.
                Toast.makeText(ChatActivity.this,
                        "Impossible de charger la discussion.", Toast.LENGTH_LONG).show();
                updateEmptyState();
            }
        });
    }

    private void startPolling() {
        if (orderId == null || myUid == null || pollRunnable != null) {
            return;
        }
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                repository.getMessages(orderId, lastCreatedAt,
                        new MessageRepository.MessageListCallback() {
                            @Override
                            public void onSuccess(List<MessageDto> fresh) {
                                if (!isFinishing() && !isDestroyed() && !fresh.isEmpty()) {
                                    appendNew(fresh, false);
                                    updateEmptyState();
                                }
                            }

                            @Override
                            public void onError(String message) {
                                // Offline or a hiccup: keep what is on screen and try
                                // again on the next tick.
                            }
                        });
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    /**
     * Adds messages we do not already have and advances the polling cursor.
     *
     * @param forceScroll true on the first load and after sending, where jumping to
     *                    the newest message is always right. During polling we only
     *                    follow along if the user was already at the bottom — yanking
     *                    the list while someone reads older messages is worse than a
     *                    missed scroll.
     */
    private void appendNew(List<MessageDto> incoming, boolean forceScroll) {
        boolean wasAtBottom = forceScroll || isAtBottom();
        int added = 0;

        for (MessageDto m : incoming) {
            if (m == null || m.id == null || seenIds.contains(m.id)) {
                continue;
            }
            seenIds.add(m.id);
            messages.add(m);
            added++;
            // The list arrives sorted ascending, so the last one carries the newest
            // timestamp — that becomes the cursor for the next request.
            if (m.created_at != null) {
                lastCreatedAt = m.created_at;
            }
        }

        if (added == 0) {
            return;
        }
        adapter.notifyDataSetChanged();
        if (wasAtBottom) {
            recycler.scrollToPosition(messages.size() - 1);
        }
    }

    private boolean isAtBottom() {
        if (messages.isEmpty()) {
            return true;
        }
        return layoutManager.findLastVisibleItemPosition() >= messages.size() - 2;
    }

    // ---- sending --------------------------------------------------------------

    private void sendMessage() {
        if (sending || !chatOpen) {
            return;
        }
        final String body = etMessage.getText().toString().trim();
        if (body.isEmpty()) {
            return;
        }

        sending = true;
        btnSend.setEnabled(false);
        // Clear immediately so the screen feels responsive; the text is put back if
        // the request fails, because silently losing a typed message is unforgivable.
        etMessage.setText("");

        repository.send(orderId, myUid, body, new MessageRepository.SendCallback() {
            @Override
            public void onSent(MessageDto message) {
                sending = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnSend.setEnabled(true);
                // Append the row the server echoed back rather than a locally built
                // one, so the id and timestamp match what everyone else will poll —
                // that is what keeps the cursor and the dedupe set honest.
                appendNew(Collections.singletonList(message), true);
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                sending = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnSend.setEnabled(true);
                etMessage.setText(body);
                etMessage.setSelection(body.length());
                Toast.makeText(ChatActivity.this,
                        "Message non envoyé. Vérifiez votre connexion.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void callCounterparty() {
        if (TextUtils.isEmpty(counterpartyPhone)) {
            Toast.makeText(this, "Numéro indisponible", Toast.LENGTH_SHORT).show();
            return;
        }
        // ACTION_DIAL opens the dialer pre-filled instead of placing the call, so no
        // CALL_PHONE permission is needed and the user stays in control.
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + counterpartyPhone)));
    }

    // ---- screen states --------------------------------------------------------

    private void applyOpenState() {
        if (chatOpen) {
            composer.setVisibility(View.VISIBLE);
            tvClosed.setVisibility(View.GONE);
        } else {
            // History stays readable after the fact; only writing is closed.
            showClosed("Aucun livreur assigné pour le moment. La discussion s'ouvrira "
                    + "dès qu'un livreur aura accepté la commande.");
        }
    }

    private void showClosed(String reason) {
        chatOpen = false;
        composer.setVisibility(View.GONE);
        tvClosed.setText(reason);
        tvClosed.setVisibility(View.VISIBLE);
    }

    private void updateEmptyState() {
        boolean showEmpty = messages.isEmpty() && chatOpen;
        tvEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }
}
