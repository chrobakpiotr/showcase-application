package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a payment gateway (see {@code ChargePaymentOutPort}) declines a charge attempt - a genuine business
 * outcome (insufficient funds, a fraud rule, an expired card, etc.), not a technical failure, so it is never retried the way a
 * transient gateway timeout would be (see {@code ResilientExecutor}). Mapped to {@code 402 Payment Required}.
 */
public class PaymentDeclinedException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentDeclinedException(final String message) {

        super(message);
    }

}
