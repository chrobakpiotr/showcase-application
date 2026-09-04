package com.cp.ecommerce.domain.payment.port.outgoing;

import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Outgoing port for looking up an order's persisted payment transaction.
 */
public interface FindPaymentTransactionOutPort {

    /**
     * @return the persisted payment transaction for {@code orderNumber}, or {@code null} if none has been recorded yet.
     */
    PaymentTransaction find(String orderNumber);

}
