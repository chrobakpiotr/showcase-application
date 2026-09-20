package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Application-layer state conflict mapped by the web adapter.
 */
public class ApplicationConflictException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationConflictException(final String message) {
        super(message);
    }
}
