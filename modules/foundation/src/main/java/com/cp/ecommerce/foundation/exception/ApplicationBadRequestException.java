package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Application-layer validation/request error mapped by the web adapter.
 */
public class ApplicationBadRequestException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ApplicationBadRequestException(final String message) {
        super(message);
    }
}
