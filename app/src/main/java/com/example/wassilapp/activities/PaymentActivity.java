package com.example.wassilapp.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wassilapp.R;
import com.example.wassilapp.database.DatabaseHelper;
import com.example.wassilapp.models.Order;
import com.example.wassilapp.models.User;
import com.example.wassilapp.remote.PaymentRepository;
import com.example.wassilapp.remote.dto.PaymentDto;
import com.example.wassilapp.utils.SessionManager;

public class PaymentActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private User currentUser;
    private String orderId;
    private double amount;

    private TextView tvOrderId, tvAmount, tvBalance;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbWallet, rbCash, rbCard, rbCIB, rbEdahabia;
    private final PaymentRepository paymentRepository = new PaymentRepository();
    private Button btnPay, btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        db = new DatabaseHelper(this);
        SessionManager session = new SessionManager(this);
        currentUser = db.getUserById(session.getUserId());
        orderId = getIntent().getStringExtra("order_id");
        amount = getIntent().getDoubleExtra("amount", 0);
        Order currentOrder = db.getOrderById(orderId);

        initUI();
        loadData();
        setupButtons();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void initUI() {
        tvOrderId = findViewById(R.id.tvPaymentOrderId);
        tvAmount = findViewById(R.id.tvPaymentAmount);
        tvBalance = findViewById(R.id.tvCurrentBalance);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        rbWallet = findViewById(R.id.rbWallet);
        rbCash = findViewById(R.id.rbCash);
        rbCard = findViewById(R.id.rbCard);
        rbCIB = findViewById(R.id.rbCIB);
        rbEdahabia = findViewById(R.id.rbEdahabia);
        btnPay = findViewById(R.id.btnConfirmPayment);
        btnCancel = findViewById(R.id.btnCancelPayment);
    }

    private void loadData() {
        tvOrderId.setText(orderId);
        tvAmount.setText(String.format("%.0f DA", amount));
        tvBalance.setText(String.format("Solde actuel: %.0f DA", currentUser.getBalance()));
    }

    private void setupButtons() {
        btnPay.setOnClickListener(v -> {
            int selectedId = rgPaymentMethod.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Choisissez un mode de paiement", Toast.LENGTH_SHORT).show();
                return;
            }

            String method = "";
            if (selectedId == rbWallet.getId()) method = "wallet";
            else if (selectedId == rbCash.getId()) method = "cash";
            else if (selectedId == rbCard.getId()) method = "card";
            else if (selectedId == rbCIB.getId()) method = "cib";
            else if (selectedId == rbEdahabia.getId()) method = "edahabia";

            processPayment(method);
        });

        btnCancel.setOnClickListener(v -> finish());
    }

    /**
     * Payment deliberately does not change the order status.
     *
     * <p>Per the cahier des charges, Paiement is a sub-view of "Détails d'une
     * livraison" and the primary method is *paiement à la livraison* — paying cannot
     * establish that a courier took the order. An earlier version set
     * 'prise_en_charge' here, which also removed the order from couriers' pending
     * lists, since that list filters on status=pending.
     */
    private void processPayment(String method) {
        switch (method) {
            case "wallet":
                payFromWallet();
                break;
            case "cash":
                explainCashOnDelivery();
                break;
            default:
                // Previously these showed the same success dialog as a real payment.
                // Nothing was charged and nothing was recorded, so the app claimed to
                // have taken money it had not — worse than admitting the gap. A card
                // rail needs a provider integration (SATIM) that does not exist.
                new AlertDialog.Builder(this)
                        .setTitle(getMethodName(method))
                        .setMessage("Le paiement par carte n'est pas encore disponible. "
                                + "Utilisez le portefeuille WASSIL ou payez en espèces "
                                + "à la livraison.")
                        .setPositiveButton("OK", null)
                        .show();
        }
    }

    /**
     * Spends the in-app balance.
     *
     * <p>The amount is not sent: pay_with_wallet reads the order's price itself,
     * checks the balance under a row lock and debits in one transaction. The previous
     * implementation compared and subtracted client-side against local SQLite, which
     * never reached profiles.balance at all.
     */
    private void payFromWallet() {
        btnPay.setEnabled(false);
        paymentRepository.payWithWallet(orderId, new PaymentRepository.PaymentCallback() {
            @Override
            public void onSuccess(PaymentDto payment) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                new AlertDialog.Builder(PaymentActivity.this)
                        .setTitle("Paiement effectué")
                        .setMessage(String.format(java.util.Locale.getDefault(),
                                "%.0f DA débités de votre portefeuille.", payment.amount))
                        .setPositiveButton("OK", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnPay.setEnabled(true);
                // The server's French message is shown as-is: "Solde insuffisant" and
                // "Cette commande a déjà été payée" are exactly what the user needs.
                Toast.makeText(PaymentActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Cash is not something this screen can complete.
     *
     * <p>The money changes hands at the door, and it is the courier who attests to
     * receiving it — record_cash_payment refuses anyone else, and refuses at all
     * before the order is delivered. So this explains rather than pretends.
     */
    private void explainCashOnDelivery() {
        new AlertDialog.Builder(this)
                .setTitle("Paiement à la livraison")
                .setMessage("Vous réglerez " + String.format(java.util.Locale.getDefault(),
                        "%.0f DA", amount) + " en espèces directement au livreur. "
                        + "Le livreur confirmera la réception au moment de la remise.")
                .setPositiveButton("Compris", (d, w) -> finish())
                .show();
    }

    private String getMethodName(String method) {
        switch (method) {
            case "wallet": return "Portefeuille WASSIL";
            case "card": return "Carte bancaire";
            case "cib": return "CIB";
            case "edahabia": return "Edahabia";
            default: return "Espèces";
        }
    }
}
