package com.cp.ecommerce.adapter.web.exception;

import java.util.Set;

import com.cp.ecommerce.adapter.common.exception.BusinessRuleException;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.exception.resource.ErrorResource;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Unit tests of the {@link GlobalExceptionHandler} behavior.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalExceptionHandlerTest {

    private static final String EXCEPTION_MESSAGE = "message";

    private final transient GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleConstraintViolationException() {

        assertResponse(createConstraintViolationExceptionResponse(), BAD_REQUEST, EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleRuntimeException() {

        assertResponse(
                handler.runtimeException(new RuntimeException()),
                INTERNAL_SERVER_ERROR,
                GlobalExceptionHandler.RUNTIME_EXCEPTION_ERROR_MESSAGE);
    }

    @Test
    void shouldHandleTechnicalProblemException() {

        assertResponse(
                handler.technicalProblemException(new TechnicalProblemException(EXCEPTION_MESSAGE)),
                INTERNAL_SERVER_ERROR,
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleBusinessRuleException() {

        assertResponse(
                handler.hexagonalRuleException(new BusinessRuleException(EXCEPTION_MESSAGE)),
                INTERNAL_SERVER_ERROR,
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleDomainObjectValidationException() {

        assertResponse(
                handler.hexagonalRuleException(new DomainObjectValidationException(EXCEPTION_MESSAGE, null)),
                INTERNAL_SERVER_ERROR,
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleResponseStatusException403() {

        assertResponse(
                handler.responseStatusException(new ResponseStatusException(FORBIDDEN, EXCEPTION_MESSAGE)),
                FORBIDDEN,
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleResponseStatusException404() {

        assertResponse(
                handler.responseStatusException(new ResponseStatusException(NOT_FOUND, EXCEPTION_MESSAGE)),
                NOT_FOUND,
                EXCEPTION_MESSAGE);
    }

    private void assertResponse(final ResponseEntity<ErrorResource> response, final HttpStatus status, final String message) {

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().message()).isEqualTo(message);
    }

    private ResponseEntity<ErrorResource> createConstraintViolationExceptionResponse() {

        final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        final Set<ConstraintViolation<Order>> violationSet = validator.validate(Order.builder().build(), Default.class);

        return handler.constraintViolationException(new ConstraintViolationException(EXCEPTION_MESSAGE, violationSet));
    }

}
