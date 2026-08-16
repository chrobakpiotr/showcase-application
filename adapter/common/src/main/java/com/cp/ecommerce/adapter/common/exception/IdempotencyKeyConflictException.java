package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a client-supplied {@code Idempotency-Key} is reused in a way that violates the idempotency contract:
 * either the same key was sent with a materially different request body, or a prior request for that same key is still being
 * processed.
 */
public class IdempotencyKeyConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdempotencyKeyConflictException(final String message) {

        super(message);
    }

}
