package com.cp.ecommerce.domain.payment.port.outgoing;

/**
 * Outgoing port for refunding a previously captured payment via the gateway.
 */
public interface RefundPaymentOutPort {

    /**
     * Refunds the charge previously identified by {@code gatewayReference} for {@code orderNumber}.
     */
    void refund(String orderNumber, String gatewayReference);

}
