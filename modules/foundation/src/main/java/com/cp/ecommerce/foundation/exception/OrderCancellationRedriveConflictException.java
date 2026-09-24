package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/** Fail-closed conflict raised when an operator cancellation-redrive command cannot be safely applied. */
public class OrderCancellationRedriveConflictException extends ApplicationConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OrderCancellationRedriveConflictException(final String message) {
        super(message);
    }
}
