package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

/**
 * Exception that should be thrown once technical problem occurred.
 */
public class TechnicalProblemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TechnicalProblemException(final String message) {

        super(message);
    }

    public TechnicalProblemException(final String message, final Throwable cause) {

        super(message, cause);
    }

}
