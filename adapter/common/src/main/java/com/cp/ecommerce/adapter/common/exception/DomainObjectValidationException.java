package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Exception that should be thrown once input validation of domain objects failed.
 */
public class DomainObjectValidationException extends ConstraintViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DomainObjectValidationException(
            final String message,
            final Set<? extends ConstraintViolation<?>> constraintViolations) {

        super(message, constraintViolations);
    }

}
