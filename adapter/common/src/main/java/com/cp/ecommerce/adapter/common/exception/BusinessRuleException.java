package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception that should be thrown once business rule is violated.
 */
public class BusinessRuleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(final String message) {

        super(message);
    }

}
