package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Payment operation identity was reused with conflicting immutable parameters.
 */
public class PaymentOperationConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PaymentOperationConflictException(final String message) {
        super(message);
    }
}
