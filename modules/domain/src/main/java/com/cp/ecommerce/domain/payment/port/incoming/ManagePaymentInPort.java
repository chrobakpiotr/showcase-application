package com.cp.ecommerce.domain.payment.port.incoming;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;

/**
 * Incoming port for capturing and refunding an order's payment.
 */
public interface ManagePaymentInPort {

    /**
     * Captures the order amount. CAPTURED, PARTIALLY_REFUNDED and REFUNDED transactions are never charged again.
     *
     * @throws PaymentDeclinedException if the gateway declines the charge.
     */
    PaymentTransaction capturePayment(String orderNumber, BigDecimal amount, PaymentMethod method);

    /**
     * Refunds the remaining refundable amount for an order. Safe to retry.
     */
    PaymentTransaction refundPayment(String orderNumber);

    /**
     * Refunds a specific amount under a durable idempotency identity, e.g. one return/RMA number.
     */
    PaymentTransaction refundPayment(String orderNumber, String refundId, BigDecimal amount);

    /**
     * Returns true while at least one durable refund for the order has not reached provider-confirmed completion.
     */
    boolean hasPendingRefunds(String orderNumber);
}
