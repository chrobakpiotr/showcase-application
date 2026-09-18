package com.cp.ecommerce.domain.payment.port.outgoing;

import java.math.BigDecimal;

/**
 * Outgoing port for refunding a captured payment.
 */
public interface RefundPaymentOutPort {

    /**
     * Refunds {@code amount}. {@code refundId} is a provider idempotency key and must be safe to replay.
     */
    void refund(String orderNumber, String gatewayReference, String refundId, BigDecimal amount);
}
