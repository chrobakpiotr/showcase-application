package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Exception thrown when a return request cannot transition to REJECTED.
 */
public class ReturnRequestNotRejectableException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReturnRequestNotRejectableException(final String message) {

        super(message);
    }

}
