package com.cp.ecommerce.domain.payment.port.outgoing;

import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Outgoing port for persisting an order's payment transaction (insert-or-update, one row per order number).
 */
public interface SavePaymentTransactionOutPort {

    /**
     * Persists {@code paymentTransaction}.
     */
    PaymentTransaction save(PaymentTransaction paymentTransaction);

    /** Creates the initial PENDING row if absent and returns the canonical current row under write lock. */
    PaymentTransaction prepareCapture(PaymentTransaction pendingPayment);

    /** Persist capture/decline completion without overwriting a later refunded terminal state. */
    PaymentTransaction saveCaptureResult(PaymentTransaction paymentTransaction);

}
