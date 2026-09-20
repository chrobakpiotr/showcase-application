package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Application-layer missing-resource error mapped by the web adapter.
 */
public class ApplicationNotFoundException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationNotFoundException(final String message) {
        super(message);
    }
}
