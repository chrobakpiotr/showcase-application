package com.cp.ecommerce.domain.payment.port.incoming;

import com.cp.ecommerce.domain.payment.PaymentTransaction;

/**
 * Incoming port for looking up the payment transaction recorded for an order.
 */
public interface GetPaymentInPort {

    /**
     * Returns the payment transaction for {@code orderNumber}. Never {@code null}: an order the saga hasn't reached its
     * payment-capture step for yet is represented as a {@link com.cp.ecommerce.domain.payment.PaymentStatus#PENDING}
     * placeholder rather than a 404 - mirrors {@code GetStockLevelInPort#getStockLevel} (see ADR 0026).
     */
    PaymentTransaction getPayment(String orderNumber);

}
