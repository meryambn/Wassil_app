package com.example.wassilapp.activities;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.wassilapp.R;
import com.example.wassilapp.adapters.DeliveryRequestAdapter;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.geo.GeoRepository;
import com.example.wassilapp.geo.RouteOverlay;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.LocationRepository;
import com.example.wassilapp.remote.OrderRepository;
import com.example.wassilapp.remote.OrderSyncManager;
import com.example.wassilapp.remote.PaymentRepository;
import com.example.wassilapp.remote.RatingRepository;
import com.example.wassilapp.remote.dto.CourierLocationDto;
import com.example.wassilapp.remote.dto.OrderDto;
import com.example.wassilapp.remote.dto.PaymentDto;
import com.example.wassilapp.remote.dto.RatingDto;
import com.example.wassilapp.services.RealTimeTrackingService;
import com.example.wassilapp.utils.SessionManager;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import java.util.Locale;
public class OrderTrackingActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST = 2001;

    private DatabaseHelper db;
    private SessionManager session;
    private OrderSyncManager syncManager;
    private final OrderRepository orderRepository = new OrderRepository();
    private final LocationRepository locationRepository = new LocationRepository();
    private final RatingRepository ratingRepository = new RatingRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();
    private Order currentOrder;
    private String orderId;
    private String userType;
    private Handler handler = new Handler();
    private Runnable updateRunnable;

    // UI Elements
    private TextView tvOrderId, tvStatus, tvPickup, tvDrop, tvPrice, tvDistance, tvTime;
    private TextView tvSenderName, tvDeliveryName;
    private TextView tvTrackingPackageType, tvTrackingWeight, tvTrackingInstructions;
    private TextView tvTrackingPaymentMethod, tvTrackingPaymentStatus;
    private TextView tvTrackingCreatedAt, tvTrackingPickedUpAt, tvTrackingDeliveredAt;
    private TextView tvSenderPhone, tvDeliveryPhone;
    private View llTrackingInstructions, llTrackingPickedUpAt, llTrackingDeliveredAt;
    private View llSenderPhone, llDeliveryPhone;
    private CardView cardPickup, cardEnRoute, cardDelivered;
    private Button btnUpdateStatus, btnContactDelivery, btnCancelOrder, btnRateCourier, btnConfirmCash;
    private View cardRatingPrompt;
    private android.widget.ScrollView scrollTracking;
    private TextView tvRatingPromptTitle, tvRatingPromptSubtitle;

    // Live tracking
    private MapView mapView;
    private View mapContainer;
    private TextView tvMapLabel, tvLocationStatus;
    private View mapCentrePin;
    private boolean mapStyleReady = false;
    private com.mapbox.maps.Style loadedStyle;
    private final GeoRepository geo = new GeoRepository();
    /** True once the endpoints are on the map, so a missing courier fix no longer blanks it. */
    private boolean routeDrawn = false;
    /** The route framing is skipped once we are following a live courier position. */
    private boolean hasCourierFix = false;
    /** Remembers whether we already asked the OS to start tracking for this screen. */
    private boolean trackingStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        syncManager = new OrderSyncManager(this);
        orderId = getIntent().getStringExtra("order_id");
        userType = getIntent().getStringExtra("user_type");

        initUI();
        loadOrderDetails();
        setupStatusSteps();
        setupButtons();
        setupMap();
        // If the courier reopens this screen mid-delivery (app restarted, phone
        // rebooted), tracking must resume rather than wait for the next transition.
        syncTrackingWithStatus();
        startRealTimeUpdates();
    }

    /**
     * Prepares the map once. Loading a style is asynchronous, so we record when it
     * is ready — moving the camera before that silently does nothing.
     */
    private void setupMap() {
        mapContainer = findViewById(R.id.mapContainer);
        mapView = findViewById(R.id.mapTracking);
        tvMapLabel = findViewById(R.id.tvMapLabel);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        mapCentrePin = findViewById(R.id.tvMapCentrePin);

        if (mapView != null) {
            mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
                mapStyleReady = true;
                loadedStyle = style;
                // Layers can only be added once a style exists to hold them, so the
                // route is drawn from here rather than from onCreate.
                drawRoute();
            });
        }
    }

    /**
     * Draws the delivery's two endpoints and the road between them.
     *
     * <p>This is possible at all only since orders started carrying coordinates —
     * every row created before that has 0,0 for both ends, which is why the check
     * below is for a usable position rather than merely a non-null one.
     */
    private void drawRoute() {
        if (!mapStyleReady || loadedStyle == null || currentOrder == null) {
            return;
        }
        final double pLat = currentOrder.getSenderLatitude();
        final double pLng = currentOrder.getSenderLongitude();
        final double dLat = currentOrder.getDeliveryLatitude();
        final double dLng = currentOrder.getDeliveryLongitude();

        // 0,0 is in the Gulf of Guinea. Treating it as "unset" rather than as a real
        // place is what stops older orders drawing a line across Africa.
        if ((pLat == 0 && pLng == 0) || (dLat == 0 && dLng == 0)) {
            return;
        }

        showMap();
        // Drawn immediately with a straight line, then replaced when the real
        // geometry arrives — the map is useful within a frame instead of after a
        // round trip, and it degrades to this permanently if Directions is down.
        RouteOverlay.draw(loadedStyle, pLat, pLng, dLat, dLng, null);
        // The fixed centre marker means nothing once real endpoints are drawn: it
        // sits wherever the frame happens to be centred, which on this route was
        // the middle of the Port of Algiers.
        if (mapCentrePin != null) {
            mapCentrePin.setVisibility(View.GONE);
        }
        if (!hasCourierFix) {
            frameRoute(pLat, pLng, dLat, dLng);
        }
        routeDrawn = true;

        geo.routeShape(pLat, pLng, dLat, dLng, shape -> {
            if (isFinishing() || isDestroyed() || shape == null || loadedStyle == null) {
                return;
            }
            RouteOverlay.draw(loadedStyle, pLat, pLng, dLat, dLng, shape);
        });
    }

    /**
     * Frames the route once the map view has real dimensions.
     *
     * <p>Deferred with post() when the view has not been measured yet: computing a
     * zoom from a width of zero silently produces a nonsense camera rather than an
     * error, and the first draw happens as the style loads, which can beat layout.
     */
    private void frameRoute(double pLat, double pLng, double dLat, double dLng) {
        if (mapView == null) {
            return;
        }
        if (mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            mapView.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    frameNow(pLat, pLng, dLat, dLng);
                }
            });
            return;
        }
        frameNow(pLat, pLng, dLat, dLng);
    }

    private void frameNow(double pLat, double pLng, double dLat, double dLng) {
        // Converted to dp: Mapbox reckons zoom in logical screen points, so handing it
        // physical pixels makes it believe the map is far wider than it really is.
        float density = getResources().getDisplayMetrics().density;
        RouteOverlay.frame(mapView.getMapboxMap(), pLat, pLng, dLat, dLng,
                mapView.getWidth() / density, mapView.getHeight() / density);
    }

    private void initUI() {
        tvOrderId = findViewById(R.id.tvTrackingOrderId);
        tvStatus = findViewById(R.id.tvTrackingStatus);
        tvPickup = findViewById(R.id.tvTrackingPickup);
        tvDrop = findViewById(R.id.tvTrackingDrop);
        tvPrice = findViewById(R.id.tvTrackingPrice);
        tvDistance = findViewById(R.id.tvTrackingDistance);
        tvTime = findViewById(R.id.tvTrackingTime);
        tvSenderName = findViewById(R.id.tvSenderName);
        tvDeliveryName = findViewById(R.id.tvDeliveryName);

        tvTrackingPackageType = findViewById(R.id.tvTrackingPackageType);
        tvTrackingWeight = findViewById(R.id.tvTrackingWeight);
        tvTrackingInstructions = findViewById(R.id.tvTrackingInstructions);
        llTrackingInstructions = findViewById(R.id.llTrackingInstructions);

        tvTrackingPaymentMethod = findViewById(R.id.tvTrackingPaymentMethod);
        tvTrackingPaymentStatus = findViewById(R.id.tvTrackingPaymentStatus);

        tvTrackingCreatedAt = findViewById(R.id.tvTrackingCreatedAt);
        tvTrackingPickedUpAt = findViewById(R.id.tvTrackingPickedUpAt);
        tvTrackingDeliveredAt = findViewById(R.id.tvTrackingDeliveredAt);
        llTrackingPickedUpAt = findViewById(R.id.llTrackingPickedUpAt);
        llTrackingDeliveredAt = findViewById(R.id.llTrackingDeliveredAt);

        tvSenderPhone = findViewById(R.id.tvSenderPhone);
        tvDeliveryPhone = findViewById(R.id.tvDeliveryPhone);
        llSenderPhone = findViewById(R.id.llSenderPhone);
        llDeliveryPhone = findViewById(R.id.llDeliveryPhone);

        cardPickup = findViewById(R.id.cardStepPickup);
        cardEnRoute = findViewById(R.id.cardStepEnRoute);
        cardDelivered = findViewById(R.id.cardStepDelivered);
        btnUpdateStatus = findViewById(R.id.btnUpdateStatus);
        btnContactDelivery = findViewById(R.id.btnContactDelivery);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        btnRateCourier = findViewById(R.id.btnRateCourier);
        btnConfirmCash = findViewById(R.id.btnConfirmCash);
        cardRatingPrompt = findViewById(R.id.cardRatingPrompt);
        scrollTracking = findViewById(R.id.scrollTracking);
        tvRatingPromptTitle = findViewById(R.id.tvRatingPromptTitle);
        tvRatingPromptSubtitle = findViewById(R.id.tvRatingPromptSubtitle);

        // This screen has a Toolbar, not the btnBack TextView the other screens use —
        // findViewById(R.id.btnBack) returned null here and crashed on open. R.id is a
        // global namespace, so that compiled fine despite the view not existing.
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarTracking);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void loadOrderDetails() {
        currentOrder = db.getOrderById(orderId);
        if (currentOrder == null) {
            Toast.makeText(this, "Commande non trouvée", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvOrderId.setText(currentOrder.getOrderId());
        tvStatus.setText(getStatusFrench(currentOrder.getStatus()));
        tvPickup.setText(currentOrder.getPickupAddress());
        tvDrop.setText(currentOrder.getDropAddress());
        tvPrice.setText(String.format("%.0f DA", currentOrder.getNegotiatedPrice()));
        tvDistance.setText(String.format("%.1f km", currentOrder.getDistance()));
        tvTime.setText(String.format("%d min", currentOrder.getEstimatedTime()));

        // Package information
        tvTrackingPackageType.setText(currentOrder.getPackageType() != null && !currentOrder.getPackageType().isEmpty()
                ? currentOrder.getPackageType() : "Colis standard");
        tvTrackingWeight.setText(String.format(Locale.FRENCH, "%.1f kg", currentOrder.getWeight()));

        String instructions = currentOrder.getSpecialInstructions();
        if (instructions != null && !instructions.trim().isEmpty()) {
            llTrackingInstructions.setVisibility(View.VISIBLE);
            tvTrackingInstructions.setText(instructions);
        } else {
            llTrackingInstructions.setVisibility(View.GONE);
        }

        // Timestamps
        tvTrackingCreatedAt.setText(currentOrder.getCreatedAt() != null ? currentOrder.getCreatedAt() : "—");

        if (currentOrder.getPickedUpAt() != null && !currentOrder.getPickedUpAt().isEmpty()) {
            llTrackingPickedUpAt.setVisibility(View.VISIBLE);
            tvTrackingPickedUpAt.setText(currentOrder.getPickedUpAt());
        } else {
            llTrackingPickedUpAt.setVisibility(View.GONE);
        }

        if (currentOrder.getDeliveredAt() != null && !currentOrder.getDeliveredAt().isEmpty()) {
            llTrackingDeliveredAt.setVisibility(View.VISIBLE);
            tvTrackingDeliveredAt.setText(currentOrder.getDeliveredAt());
        } else {
            llTrackingDeliveredAt.setVisibility(View.GONE);
        }

        User sender = db.getUserById(currentOrder.getSenderId());
        tvSenderName.setText(sender != null ? sender.getFullName() : "N/A");
        if (sender != null && sender.getPhone() != null && !sender.getPhone().isEmpty() && "admin".equals(userType)) {
            llSenderPhone.setVisibility(View.VISIBLE);
            tvSenderPhone.setText(sender.getPhone());
        } else {
            llSenderPhone.setVisibility(View.GONE);
        }

        if (currentOrder.getDeliveryId() > 0) {
            User delivery = db.getUserById(currentOrder.getDeliveryId());
            tvDeliveryName.setText(delivery != null ? delivery.getFullName() : "Livreur assigné");
            if (delivery != null && delivery.getPhone() != null && !delivery.getPhone().isEmpty() && "admin".equals(userType)) {
                llDeliveryPhone.setVisibility(View.VISIBLE);
                tvDeliveryPhone.setText(delivery.getPhone());
            } else {
                llDeliveryPhone.setVisibility(View.GONE);
            }
        } else {
            tvDeliveryName.setText("En attente d'assignation");
            llDeliveryPhone.setVisibility(View.GONE);
        }

        // Payment lookup from Supabase
        paymentRepository.getForOrder(currentOrder.getOrderId(), new PaymentRepository.LookupCallback() {
            @Override
            public void onResult(PaymentDto payment) {
                if (isFinishing() || isDestroyed()) return;
                if (payment != null) {
                    String methodText = "cash".equals(payment.method) ? "Espèces (À la livraison)" :
                            ("wallet".equals(payment.method) ? "Portefeuille WASSIL" : payment.method);
                    tvTrackingPaymentMethod.setText(methodText);

                    if ("completed".equals(payment.status)) {
                        tvTrackingPaymentStatus.setText(String.format(Locale.FRENCH, "Réglé (%.0f DA)", payment.amount));
                        tvTrackingPaymentStatus.setTextColor(0xFF4CAF50);
                    } else if ("pending".equals(payment.status)) {
                        tvTrackingPaymentStatus.setText("En attente de règlement");
                        tvTrackingPaymentStatus.setTextColor(0xFFF57C00);
                    } else {
                        tvTrackingPaymentStatus.setText(payment.status);
                        tvTrackingPaymentStatus.setTextColor(0xFF757575);
                    }
                } else {
                    tvTrackingPaymentMethod.setText("À la livraison (Espèces)");
                    if ("livre".equals(currentOrder.getStatus())) {
                        tvTrackingPaymentStatus.setText("En attente de confirmation espèces");
                        tvTrackingPaymentStatus.setTextColor(0xFFF57C00);
                    } else {
                        tvTrackingPaymentStatus.setText("Non réglé (À régler à la remise)");
                        tvTrackingPaymentStatus.setTextColor(0xFF757575);
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                tvTrackingPaymentMethod.setText("À la livraison (Espèces)");
                tvTrackingPaymentStatus.setText("Non disponible");
            }
        });

        // Set status color
        int color = getStatusColor(currentOrder.getStatus());
        tvStatus.setTextColor(getColor(color));
    }

    /**
     * The delivery lifecycle in order (cahier des charges p.3), excluding 'pending'
     * (no courier yet) and 'annule' (not a stage but an exit).
     */
    private static final String[] LIFECYCLE = {
            "prise_en_charge", "vers_depart", "colis_recupere", "en_route", "livre"
    };

    /** Position of a status in the lifecycle, or -1 if it is not a stage. */
    private int lifecycleIndex(String status) {
        for (int i = 0; i < LIFECYCLE.length; i++) {
            if (LIFECYCLE[i].equals(status)) {
                return i;
            }
        }
        return -1;
    }

    private void setupStatusSteps() {
        String status = currentOrder.getStatus();
        int reached = lifecycleIndex(status);

        // Coarse 3-card summary kept from before.
        cardPickup.setCardBackgroundColor(getColor(android.R.color.white));
        cardEnRoute.setCardBackgroundColor(getColor(android.R.color.white));
        cardDelivered.setCardBackgroundColor(getColor(android.R.color.white));
        if (reached >= 0) cardPickup.setCardBackgroundColor(getColor(R.color.status_prise_en_charge));
        if (reached >= 3) cardEnRoute.setCardBackgroundColor(getColor(R.color.status_en_route));
        if (reached >= 4) cardDelivered.setCardBackgroundColor(getColor(R.color.status_livre));

        // Detailed stepper. Comparing positions instead of switching on each status
        // means a status added later cannot silently fall through an unhandled case.
        int[] stepIds = {R.id.tvStep1, R.id.tvStep2, R.id.tvStep3, R.id.tvStep4, R.id.tvStep5};
        for (int i = 0; i < stepIds.length; i++) {
            TextView step = findViewById(stepIds[i]);
            if (step == null) {
                continue;
            }
            boolean done = i < reached;
            boolean current = i == reached;
            String label = getStatusFrench(LIFECYCLE[i]);

            if (done) {
                step.setText("✅  " + label);
                step.setTextColor(getColor(R.color.status_livre));
            } else if (current) {
                step.setText("🔵  " + label);
                step.setTextColor(getColor(R.color.status_en_route));
            } else {
                step.setText("○  " + label);
                step.setTextColor(0xFF9E9E9E);
            }
        }

        // A cancelled order never reached any stage, so say that plainly rather than
        // showing five grey steps that imply it is still in progress.
        View lifecycleBox = findViewById(R.id.llLifecycle);
        if (lifecycleBox != null) {
            lifecycleBox.setVisibility("annule".equals(status) ? View.GONE : View.VISIBLE);
        }
    }

    private void setupButtons() {
        int index = lifecycleIndex(currentOrder.getStatus());
        boolean canAdvance = index >= 0 && index < LIFECYCLE.length - 1;

        // Update status button (only for delivery user)
        if (userType.equals("delivery") && canAdvance) {
            // Label the button with the step it will move to, so the courier knows
            // what they are confirming instead of pressing a generic "update".
            btnUpdateStatus.setText(getStatusFrench(LIFECYCLE[index + 1]));
            btnUpdateStatus.setVisibility(android.view.View.VISIBLE);
            btnUpdateStatus.setOnClickListener(v -> updateOrderStatus());
        } else {
            btnUpdateStatus.setVisibility(android.view.View.GONE);
        }

        setupContactButton();
        setupRatingButton();
        setupCashPaymentButton();

        // Cancel button (only for sender, only if not delivered)
        if (userType.equals("sender") && !currentOrder.getStatus().equals("livre")
                && !currentOrder.getStatus().equals("en_route")) {
            btnCancelOrder.setVisibility(android.view.View.VISIBLE);
            btnCancelOrder.setOnClickListener(v -> cancelOrder());
        } else {
            btnCancelOrder.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * Wires the contact button to this order's conversation.
     *
     * <p>"The other party" is not a fixed role: the sender talks to the courier, the
     * courier talks to the sender. Resolving that here rather than inside ChatActivity
     * keeps the chat screen free of any knowledge about roles, and this screen already
     * holds the order.
     *
     * <p>The button's label used to be hardcoded "Contacter le livreur" in the layout,
     * so the courier was being invited to contact himself. It is set from code now.
     * The old handler only showed the phone number in a Toast, which the user could
     * not even copy.
     */
    private void setupContactButton() {
        boolean iAmSender = "sender".equals(userType);
        int counterpartyId = iAmSender ? currentOrder.getDeliveryId() : currentOrder.getSenderId();
        String counterpartyName = iAmSender
                ? currentOrder.getDeliveryName()
                : currentOrder.getSenderName();

        // Phone lives on the local users row, which OrderSyncManager fills in from the
        // embedded profile on every sync, so it is present for both roles.
        User counterparty = counterpartyId > 0 ? db.getUserById(counterpartyId) : null;
        String phone = counterparty != null ? counterparty.getPhone() : null;

        // A conversation needs two sides. Before a courier accepts, a message would
        // have no reader — pre-assignment negotiation goes through offers instead.
        boolean chatOpen = currentOrder.getDeliveryId() > 0;

        // If admin is viewing, contact button is hidden because admin does not participate in chat
        if ("admin".equals(userType)) {
            btnContactDelivery.setVisibility(View.GONE);
            return;
        }

        btnContactDelivery.setVisibility(View.VISIBLE);
        btnContactDelivery.setText(iAmSender ? "Discuter avec le livreur" : "Discuter avec le client");
        btnContactDelivery.setOnClickListener(v -> {
            Intent chat = new Intent(this, ChatActivity.class);
            chat.putExtra(ChatActivity.EXTRA_ORDER_ID, currentOrder.getOrderId());
            chat.putExtra(ChatActivity.EXTRA_NAME, counterpartyName);
            chat.putExtra(ChatActivity.EXTRA_PHONE, phone);
            chat.putExtra(ChatActivity.EXTRA_CHAT_OPEN, chatOpen);
            startActivity(chat);
        });
    }

    /**
     * Offers the review form, but only when it can actually succeed.
     *
     * <p>Three conditions, and the database independently enforces all of them again:
     * ratings_insert requires rater_id = auth.uid(), that the order belongs to the
     * rater, and that its status is already 'livre'. Hiding the button is a courtesy,
     * not a control — a client that called the endpoint anyway would be rejected.
     */
    private void setupRatingButton() {
        boolean canRate = "sender".equals(userType)
                && "livre".equals(currentOrder.getStatus())
                && currentOrder.getDeliveryId() > 0;

        if (!canRate) {
            // Covers pending, prise_en_charge, vers_depart, colis_recupere, en_route
            // and annule: the whole prompt is hidden, not just disabled, so there is
            // nothing to tap before the delivery has actually happened.
            cardRatingPrompt.setVisibility(View.GONE);
            return;
        }

        // Asked of the server rather than remembered locally: the sender may have
        // reviewed this delivery from another device, and a local flag would happily
        // offer a form whose submission the unique constraint then rejects.
        ratingRepository.findMine(orderId, session.getSupabaseUid(),
                new RatingRepository.ExistingCallback() {
                    @Override
                    public void onResult(RatingDto existing) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        // Was it hidden until now? Then the delivery just completed
                        // while this screen was open, and the prompt has appeared
                        // above the current scroll position where it would go unseen.
                        boolean justAppeared = cardRatingPrompt.getVisibility() != View.VISIBLE;
                        cardRatingPrompt.setVisibility(View.VISIBLE);
                        boolean alreadyRated = existing != null;

                        // The celebratory ask is what must not come back once the
                        // sender has answered it. The verdict below it stays, because
                        // it is the only place they can see what they gave.
                        tvRatingPromptTitle.setVisibility(alreadyRated ? View.GONE : View.VISIBLE);
                        tvRatingPromptSubtitle.setVisibility(alreadyRated ? View.GONE : View.VISIBLE);

                        if (alreadyRated) {
                            // Reviews are immutable by design (no UPDATE policy on
                            // ratings), so show the verdict rather than a dead button.
                            btnRateCourier.setEnabled(false);
                            btnRateCourier.setText(String.format(Locale.getDefault(),
                                    "Vous avez noté %d/5", existing.stars));
                        } else {
                            btnRateCourier.setEnabled(true);
                            btnRateCourier.setText("Évaluer le livreur");
                            btnRateCourier.setOnClickListener(v -> showRatingDialog());
                        }

                        // Only when it has just appeared, and only for an
                        // unanswered prompt: scrolling the screen out from under
                        // someone who is reading it would be worse than the
                        // prompt being missed. post() waits for the card to be
                        // laid out, since scrolling to a view with no height yet
                        // does nothing.
                        if (justAppeared && !alreadyRated && scrollTracking != null) {
                            scrollTracking.post(() -> scrollTracking.smoothScrollTo(0, 0));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Offline: hiding beats offering a form that cannot be sent.
                        if (!isFinishing() && !isDestroyed()) {
                            cardRatingPrompt.setVisibility(View.GONE);
                        }
                    }
                });
    }

    /**
     * Lets the courier attest that they were handed cash.
     *
     * <p>Shown only to the assigned courier, only on a delivered order, and only
     * while no payment exists. record_cash_payment enforces all three again, plus a
     * unique constraint on order_id — so a stale screen or a double tap cannot
     * record the same delivery as paid twice.
     */
    private void setupCashPaymentButton() {
        if (btnConfirmCash == null) {
            return;
        }
        boolean courierOnDelivered = "delivery".equals(userType)
                && "livre".equals(currentOrder.getStatus());
        if (!courierOnDelivered) {
            btnConfirmCash.setVisibility(View.GONE);
            return;
        }

        // Asked of the server rather than assumed: the sender may have paid from
        // their wallet, in which case there is no cash to confirm at all.
        paymentRepository.getForOrder(currentOrder.getOrderId(),
                new PaymentRepository.LookupCallback() {
                    @Override
                    public void onResult(PaymentDto payment) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        btnConfirmCash.setVisibility(View.VISIBLE);
                        if (payment != null) {
                            btnConfirmCash.setEnabled(false);
                            btnConfirmCash.setText("wallet".equals(payment.method)
                                    ? "Déjà payé par portefeuille"
                                    : "Paiement en espèces confirmé");
                        } else {
                            btnConfirmCash.setEnabled(true);
                            btnConfirmCash.setText("Confirmer le paiement en espèces");
                            btnConfirmCash.setOnClickListener(v -> confirmCashReceived());
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isFinishing() && !isDestroyed()) {
                            btnConfirmCash.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void confirmCashReceived() {
        new AlertDialog.Builder(this)
                .setTitle("Paiement reçu")
                .setMessage(String.format(Locale.getDefault(),
                        "Confirmez-vous avoir reçu %.0f DA en espèces ? "
                                + "Cette confirmation est définitive.",
                        currentOrder.getNegotiatedPrice()))
                .setPositiveButton("Confirmer", (d, w) -> {
                    btnConfirmCash.setEnabled(false);
                    paymentRepository.recordCashPayment(currentOrder.getOrderId(),
                            new PaymentRepository.PaymentCallback() {
                                @Override
                                public void onSuccess(PaymentDto payment) {
                                    if (isFinishing() || isDestroyed()) {
                                        return;
                                    }
                                    Toast.makeText(OrderTrackingActivity.this,
                                            "Paiement enregistré", Toast.LENGTH_SHORT).show();
                                    setupCashPaymentButton();
                                }

                                @Override
                                public void onError(String message) {
                                    if (isFinishing() || isDestroyed()) {
                                        return;
                                    }
                                    Toast.makeText(OrderTrackingActivity.this,
                                            message, Toast.LENGTH_LONG).show();
                                    setupCashPaymentButton();
                                }
                            });
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showRatingDialog() {
        User courier = db.getUserById(currentOrder.getDeliveryId());
        // ratee_id is a profiles.id, so the courier's Supabase uid is required here —
        // the local integer id means nothing to the server.
        final String rateeUid = courier != null ? courier.getSupabaseUid() : null;
        if (rateeUid == null) {
            Toast.makeText(this, "Livreur introuvable — réessayez plus tard",
                    Toast.LENGTH_LONG).show();
            return;
        }

        View content = getLayoutInflater().inflate(R.layout.dialog_rate_courier, null);
        TextView target = content.findViewById(R.id.tvRateTarget);
        TextView starsLabel = content.findViewById(R.id.tvRateStarsLabel);
        RatingBar bar = content.findViewById(R.id.ratingBarCourier);
        EditText comment = content.findViewById(R.id.etRateComment);

        String courierName = currentOrder.getDeliveryName();
        target.setText(courierName != null && !courierName.isEmpty() ? courierName : "Votre livreur");
        starsLabel.setText(starsWording((int) bar.getRating()));
        bar.setOnRatingBarChangeListener(
                (rb, value, fromUser) -> starsLabel.setText(starsWording((int) value)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Noter la livraison")
                .setView(content)
                .setPositiveButton("Envoyer", null)
                .setNegativeButton("Annuler", null)
                .create();
        dialog.show();

        // The click listener is attached after show() on purpose. A listener passed to
        // setPositiveButton dismisses the dialog the moment it fires, which would close
        // the form while the request is still in flight and leave nowhere to report a
        // failure — or to give the typed comment back.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int stars = (int) bar.getRating();
            if (stars < 1) {
                Toast.makeText(this, "Choisissez au moins une étoile", Toast.LENGTH_SHORT).show();
                return;
            }
            v.setEnabled(false);
            ratingRepository.submit(orderId, session.getSupabaseUid(), rateeUid, stars,
                    comment.getText().toString(), new RatingRepository.SubmitCallback() {
                        @Override
                        public void onSubmitted(RatingDto rating) {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            dialog.dismiss();
                            Toast.makeText(OrderTrackingActivity.this,
                                    "Merci pour votre note", Toast.LENGTH_SHORT).show();
                            // Re-reads from the server, so the button now reflects a
                            // fact the database confirmed rather than an optimistic guess.
                            setupRatingButton();
                        }

                        @Override
                        public void onError(String message) {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            v.setEnabled(true);
                            Toast.makeText(OrderTrackingActivity.this,
                                    "Note non enregistrée. " + message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    /** Says out loud what a star count means, so the number is not the only signal. */
    private String starsWording(int stars) {
        switch (stars) {
            case 1: return "★ Très insatisfait";
            case 2: return "★★ Insatisfait";
            case 3: return "★★★ Correct";
            case 4: return "★★★★ Satisfait";
            case 5: return "★★★★★ Excellent";
            default: return "Choisissez une note";
        }
    }

    private void updateOrderStatus() {
        String currentStatus = currentOrder.getStatus();
        int index = lifecycleIndex(currentStatus);

        // Advance one step along the lifecycle instead of jumping. Deriving the next
        // status from the array keeps this in step with the stepper UI automatically.
        if (index < 0 || index >= LIFECYCLE.length - 1) {
            return; // not a stage, or already delivered
        }
        final String newStatus = LIFECYCLE[index + 1];

        // The courier's fee is NOT credited here: the orders_credit_courier trigger
        // does it when the row reaches 'livre'. Doing it client-side meant the balance
        // existed on one device only, for whatever amount the client claimed.

        // Cloud-first: this transition has to reach the sender's device, and a
        // local-only write would be reverted by the next sync anyway.
        btnUpdateStatus.setEnabled(false);
        orderRepository.updateStatus(currentOrder.getOrderId(), newStatus,
                new OrderRepository.OrderCallback() {
                    @Override
                    public void onSuccess(OrderDto order) {
                        btnUpdateStatus.setEnabled(true);
                        db.updateOrderStatus(currentOrder.getOrderId(), newStatus);
                        Toast.makeText(OrderTrackingActivity.this,
                                "Statut mis à jour: " + getStatusFrench(newStatus),
                                Toast.LENGTH_SHORT).show();
                        loadOrderDetails();
                        setupStatusSteps();
                        setupButtons();
                    }

                    @Override
                    public void onError(String message) {
                        btnUpdateStatus.setEnabled(true);
                        Toast.makeText(OrderTrackingActivity.this,
                                "Mise à jour impossible: " + message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void cancelOrder() {
        // Cloud-first: a cancellation only written locally would be silently
        // reverted by the next sync, and the courier would never learn about it.
        btnCancelOrder.setEnabled(false);
        orderRepository.updateStatus(currentOrder.getOrderId(), "annule",
                new OrderRepository.OrderCallback() {
                    @Override
                    public void onSuccess(OrderDto order) {
                        db.updateOrderStatus(currentOrder.getOrderId(), "annule");
                        Toast.makeText(OrderTrackingActivity.this, "Commande annulée",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        btnCancelOrder.setEnabled(true);
                        Toast.makeText(OrderTrackingActivity.this,
                                "Annulation impossible: " + message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void startRealTimeUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // This used to re-read local SQLite, which cannot change from another
                // device — so it could never actually observe the counterparty. Pull
                // from Supabase first, then re-read the refreshed cache.
                syncManager.syncMyOrders(session.getSupabaseUid(), session.getUserId(),
                        session.getUserName(), new OrderSyncManager.SyncCallback() {
                            @Override
                            public void onSynced(int orderCount) {
                                applyRefreshedOrder();
                            }

                            @Override
                            public void onError(String message) {
                                // Offline: keep showing the cached order silently.
                            }
                        });
                // Location piggybacks on this existing loop rather than starting a
                // second timer. I proposed 10s in the design; 5s here costs one tiny
                // row fetch and avoids a second Handler to keep in sync.
                fetchCourierLocation();
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(updateRunnable);
    }

    private void applyRefreshedOrder() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Order updatedOrder = db.getOrderById(orderId);
        if (updatedOrder != null && !updatedOrder.getStatus().equals(currentOrder.getStatus())) {
            currentOrder = updatedOrder;
            loadOrderDetails();
            setupStatusSteps();
            setupButtons();
            syncTrackingWithStatus();
        }
    }

    /**
     * Starts or stops position sharing based on the delivery's status.
     *
     * <p>Only the courier's own device publishes, and only while the parcel is
     * actually moving ('en_route'). Once the order is delivered or cancelled the
     * service is stopped — otherwise it would keep the GPS radio alive and drain the
     * courier's battery for the rest of the day.
     */
    private void syncTrackingWithStatus() {
        if (!"delivery".equals(userType) || currentOrder == null) {
            return;
        }
        // Share position during every phase where the courier is actually moving —
        // including the trip TO the pickup point, which the sender wants to see. It
        // used to start only at 'en_route', so the whole approach leg was invisible.
        String s = currentOrder.getStatus();
        boolean shouldTrack = "vers_depart".equals(s)
                || "colis_recupere".equals(s)
                || "en_route".equals(s);

        if (shouldTrack && !trackingStarted) {
            if (hasLocationPermission()) {
                startTrackingService();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST);
            }
        } else if (!shouldTrack && trackingStarted) {
            stopTrackingService();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startTrackingService() {
        String uid = session.getSupabaseUid();
        if (uid == null || orderId == null) {
            return;
        }
        Intent intent = new Intent(this, RealTimeTrackingService.class);
        intent.putExtra(RealTimeTrackingService.EXTRA_ORDER_ID, orderId);
        intent.putExtra(RealTimeTrackingService.EXTRA_COURIER_UID, uid);
        ContextCompat.startForegroundService(this, intent);
        trackingStarted = true;
    }

    private void stopTrackingService() {
        stopService(new Intent(this, RealTimeTrackingService.class));
        trackingStarted = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTrackingService();
        } else {
            // Refusal is a legitimate choice: the delivery still works, the sender
            // just cannot see the position. Say so instead of failing silently.
            Toast.makeText(this,
                    "Sans autorisation de localisation, l'expéditeur ne pourra pas suivre la livraison",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Fetches the courier's last known position and centres the map on it.
     *
     * <p>No ownership check is done here: locations_select returns a row only to the
     * courier, the sender of that order, or an admin. An unauthorised viewer simply
     * gets an empty result, which is indistinguishable from "no position yet".
     */
    private void fetchCourierLocation() {
        if (orderId == null) {
            return;
        }
        locationRepository.getForOrder(orderId, new LocationRepository.LocationCallback() {
            @Override
            public void onSuccess(CourierLocationDto location) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showMap();
                hasCourierFix = true;
                tvLocationStatus.setText("Dernière position : " + shortTime(location.updated_at));
                if (mapStyleReady && mapView != null) {
                    // Point.fromLngLat takes LONGITUDE first — a classic source of
                    // markers landing in the wrong hemisphere.
                    mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                            .center(Point.fromLngLat(location.lng, location.lat))
                            .zoom(14.0)
                            .build());
                }
            }

            @Override
            public void onNotFound() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                // The courier has not reported a position yet. That used to hide the
                // whole map, which now also hides the route and the two endpoints —
                // information that is available and useful on its own.
                if (routeDrawn) {
                    showMap();
                    tvLocationStatus.setText("Livreur pas encore localisé — trajet prévu affiché");
                } else {
                    hideMap("Position non disponible pour le moment");
                }
            }

            @Override
            public void onError(String message) {
                // Keep whatever the map is already showing rather than blanking it.
            }
        });
    }

    private void showMap() {
        if (mapContainer != null) mapContainer.setVisibility(View.VISIBLE);
        if (tvMapLabel != null) tvMapLabel.setVisibility(View.VISIBLE);
        if (tvLocationStatus != null) tvLocationStatus.setVisibility(View.VISIBLE);
    }

    private void hideMap(String reason) {
        if (mapContainer != null) mapContainer.setVisibility(View.GONE);
        // Keep the label + reason visible so the absence of a map is explained.
        if (tvMapLabel != null) tvMapLabel.setVisibility(View.VISIBLE);
        if (tvLocationStatus != null) {
            tvLocationStatus.setVisibility(View.VISIBLE);
            tvLocationStatus.setText(reason);
        }
    }

    /** "2026-08-16T10:28:03+00:00" -> "10:28". */
    private String shortTime(String iso) {
        if (iso == null || iso.length() < 16) {
            return "";
        }
        return iso.substring(11, 16);
    }

    private String getStatusFrench(String status) {
        switch (status) {
            // Labels follow the cahier des charges p.3 wording.
            case "pending": return "Recherche d'un livreur";
            case "prise_en_charge": return "Livreur accepté";
            case "vers_depart": return "En route vers le point de départ";
            case "colis_recupere": return "Colis récupéré";
            case "en_route": return "En cours de livraison";
            case "livre": return "Livré";
            case "annule": return "Annulé";
            default: return status;
        }
    }

    private int getStatusColor(String status) {
        switch (status) {
            case "en_route": return R.color.status_en_route;
            case "livre": return R.color.status_livre;
            case "prise_en_charge":
            case "vers_depart":
            case "colis_recupere": return R.color.status_prise_en_charge;
            default: return R.color.text_secondary;
        }
    }

    // Mapbox's MapView holds native rendering resources and must be told about the
    // Activity lifecycle, exactly as AdminHomeActivity and MapboxActivity already do.
    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        if (mapView != null) {
            mapView.onDestroy();
        }
        // NOTE: the tracking service is deliberately NOT stopped here. Closing this
        // screen must not stop a delivery in progress — the courier will lock their
        // phone while driving. It stops when the status leaves 'en_route'.
    }
}
