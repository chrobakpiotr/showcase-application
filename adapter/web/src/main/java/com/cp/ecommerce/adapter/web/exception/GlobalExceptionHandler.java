package com.cp.ecommerce.adapter.web.exception;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.exception.BusinessRuleException;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.exception.resource.ErrorResource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Class serving exception handling functionality.
 */
@ControllerAdvice(annotations = Component.class)
@Slf4j
public class GlobalExceptionHandler {

    public static final String RUNTIME_EXCEPTION_ERROR_MESSAGE = "Could not process your request";

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResource> constraintViolationException(
            final ConstraintViolationException constraintViolationException) {

        return createExceptionResponse(
                constraintViolationException,
                constraintViolationException.getMessage(),
                ResponseEntity.badRequest());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler({ DomainObjectValidationException.class, BusinessRuleException.class })
    public ResponseEntity<ErrorResource> hexagonalRuleException(final RuntimeException runtimeException) {

        return createExceptionResponse(runtimeException, runtimeException.getMessage(), ResponseEntity.internalServerError());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(TechnicalProblemException.class)
    public ResponseEntity<ErrorResource> technicalProblemException(final RuntimeException runtimeException) {

        return createExceptionResponse(runtimeException, runtimeException.getMessage(), ResponseEntity.internalServerError());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResource> runtimeException(final RuntimeException exception) {

        return createExceptionResponse(exception, RUNTIME_EXCEPTION_ERROR_MESSAGE, ResponseEntity.internalServerError());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResource> responseStatusException(final ResponseStatusException exception) {

        return createExceptionResponse(exception, exception.getReason(), ResponseEntity.status(exception.getStatusCode()));
    }

    private ResponseEntity<ErrorResource> createExceptionResponse(
            final Exception exception,
            final String message,
            final ResponseEntity.BodyBuilder builder) {

        log.error("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
        return builder.body(ErrorResource.builder().message(message).id(UUID.randomUUID().toString()).build());
    }

}
