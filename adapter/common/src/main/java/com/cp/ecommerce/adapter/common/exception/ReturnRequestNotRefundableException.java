package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception thrown when a return request cannot transition to REFUNDED.
 */
public class ReturnRequestNotRefundableException extends BusinessRuleException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReturnRequestNotRefundableException(final String message) {

        super(message);
    }

}
