package com.cp.ecommerce.domain.payment.port.outgoing;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.domain.order.PaymentMethod;

/**
 * Outgoing port abstracting the actual payment gateway integration - implemented by a mock/simulated adapter (see ADR 0030),
 * the same "simulate an external system behind a real port so the saga integration is genuinely exercised" approach already
 * taken for e.g. {@code SendMessageOutPort}/RabbitMQ.
 */
public interface ChargePaymentOutPort {

    /**
     * Charges {@code amount} for {@code orderNumber} via {@code method}.
     *
     * @return an opaque gateway-assigned reference identifying the charge.
     * @throws PaymentDeclinedException if the gateway declines the charge.
     */
    String charge(String orderNumber, BigDecimal amount, PaymentMethod method);

}
