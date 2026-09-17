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

}
