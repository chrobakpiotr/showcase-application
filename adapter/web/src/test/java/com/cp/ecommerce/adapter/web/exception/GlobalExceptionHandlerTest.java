package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;
import java.util.Set;

import com.cp.ecommerce.adapter.common.exception.BusinessRuleException;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.adapter.common.exception.IdempotencyKeyConflictException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
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

        assertProblem(createConstraintViolationExceptionResponse(), BAD_REQUEST, "Constraint Violation", EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleRuntimeException() {

        assertProblem(
                handler.runtimeException(new RuntimeException()),
                INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                GlobalExceptionHandler.RUNTIME_EXCEPTION_ERROR_MESSAGE);
    }

    @Test
    void shouldHandleTechnicalProblemException() {

        assertProblem(
                handler.technicalProblemException(new TechnicalProblemException(EXCEPTION_MESSAGE)),
                INTERNAL_SERVER_ERROR,
                "Technical Problem",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleBusinessRuleException() {

        assertProblem(
                handler.businessRuleException(new BusinessRuleException(EXCEPTION_MESSAGE)),
                INTERNAL_SERVER_ERROR,
                "Business Rule Violation",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleDomainObjectValidationException() {

        assertProblem(
                handler.domainObjectValidationException(new DomainObjectValidationException(EXCEPTION_MESSAGE, null)),
                INTERNAL_SERVER_ERROR,
                "Domain Validation Error",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleIdempotencyKeyConflictException() {

        assertProblem(
                handler.idempotencyKeyConflictException(new IdempotencyKeyConflictException(EXCEPTION_MESSAGE)),
                CONFLICT,
                "Idempotency Key Conflict",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleResponseStatusException403() {

        assertProblem(
                handler.responseStatusException(new ResponseStatusException(FORBIDDEN, EXCEPTION_MESSAGE)),
                FORBIDDEN,
                FORBIDDEN.getReasonPhrase(),
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleResponseStatusException404() {

        assertProblem(
                handler.responseStatusException(new ResponseStatusException(NOT_FOUND, EXCEPTION_MESSAGE)),
                NOT_FOUND,
                NOT_FOUND.getReasonPhrase(),
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldFallBackToGenericProblemTypeForNonStandardStatusCode() {

        final HttpStatusCode nonStandardStatus = HttpStatusCode.valueOf(599);

        final ProblemDetail problemDetail = handler
                .responseStatusException(new ResponseStatusException(nonStandardStatus, EXCEPTION_MESSAGE));

        assertThat(problemDetail.getStatus()).isEqualTo(599);
        assertThat(problemDetail.getTitle()).isNull();
        assertThat(problemDetail.getType()).isEqualTo(URI.create("urn:problem-type:error"));
    }

    @Test
    void shouldAddCorrelatableErrorIdExtensionMember() {

        final ProblemDetail problemDetail = handler.runtimeException(new RuntimeException());

        assertThat(problemDetail.getProperties()).containsKey("errorId");
        assertThat(problemDetail.getProperties().get("errorId")).asString().isNotBlank();
    }

    private void assertProblem(
            final ProblemDetail problemDetail,
            final HttpStatus status,
            final String title,
            final String detail) {

        assertThat(problemDetail.getStatus()).isEqualTo(status.value());
        assertThat(problemDetail.getTitle()).isEqualTo(title);
        assertThat(problemDetail.getDetail()).isEqualTo(detail);
        assertThat(problemDetail.getProperties()).containsKey("errorId");
    }

    private ProblemDetail createConstraintViolationExceptionResponse() {

        final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        final Set<ConstraintViolation<Order>> violationSet = validator.validate(Order.builder().build(), Default.class);

        return handler.constraintViolationException(new ConstraintViolationException(EXCEPTION_MESSAGE, violationSet));
    }

}
