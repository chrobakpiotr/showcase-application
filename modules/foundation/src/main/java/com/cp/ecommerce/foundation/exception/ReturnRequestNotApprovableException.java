package com.cp.ecommerce.foundation.exception;

import java.io.Serial;

/**
 * Exception thrown when a return request cannot transition to APPROVED.
 */
public class ReturnRequestNotApprovableException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReturnRequestNotApprovableException(final String message) {

        super(message);
    }

}
