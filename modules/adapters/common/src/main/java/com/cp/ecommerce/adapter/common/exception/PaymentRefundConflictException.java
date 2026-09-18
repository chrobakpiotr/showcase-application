package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Business conflict raised when a payment refund identity or amount cannot be accepted.
 */
public class PaymentRefundConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentRefundConflictException(final String message) {

        super(message);
    }
}
