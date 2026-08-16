package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import com.cp.ecommerce.adapter.common.exception.BusinessRuleException;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.adapter.common.exception.IdempotencyKeyConflictException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Class serving exception handling functionality.
 *
 * <p>
 * Every response follows <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a> ("Problem Details for HTTP APIs"):
 * Spring serializes a returned {@link ProblemDetail} as {@code application/problem+json} with the standard
 * {@code type}/{@code title}/{@code status}/{@code detail} members, so clients get a machine-readable, self-describing error
 * shape instead of an ad-hoc one. An {@code errorId} extension member is added to every response and also written to the server
 * log, so a specific failure can be correlated between what the client saw and the corresponding log line.
 */
@RestControllerAdvice(annotations = Component.class)
@Slf4j
public class GlobalExceptionHandler {

    public static final String RUNTIME_EXCEPTION_ERROR_MESSAGE = "Could not process your request";

    private static final String ERROR_ID_PROPERTY = "errorId";
    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:";

    private static final URI TYPE_CONSTRAINT_VIOLATION = URI.create(PROBLEM_TYPE_PREFIX + "constraint-violation");
    private static final URI TYPE_DOMAIN_VALIDATION_ERROR = URI.create(PROBLEM_TYPE_PREFIX + "domain-validation-error");
    private static final URI TYPE_BUSINESS_RULE_VIOLATION = URI.create(PROBLEM_TYPE_PREFIX + "business-rule-violation");
    private static final URI TYPE_IDEMPOTENCY_KEY_CONFLICT = URI.create(PROBLEM_TYPE_PREFIX + "idempotency-key-conflict");
    private static final URI TYPE_TECHNICAL_PROBLEM = URI.create(PROBLEM_TYPE_PREFIX + "technical-problem");
    private static final URI TYPE_INTERNAL_ERROR = URI.create(PROBLEM_TYPE_PREFIX + "internal-error");

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolationException(final ConstraintViolationException exception) {

        return problemDetail(exception, BAD_REQUEST, TYPE_CONSTRAINT_VIOLATION, "Constraint Violation", exception.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(DomainObjectValidationException.class)
    public ProblemDetail domainObjectValidationException(final DomainObjectValidationException exception) {

        return problemDetail(
                exception,
                INTERNAL_SERVER_ERROR,
                TYPE_DOMAIN_VALIDATION_ERROR,
                "Domain Validation Error",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail idempotencyKeyConflictException(final IdempotencyKeyConflictException exception) {

        return problemDetail(
                exception,
                CONFLICT,
                TYPE_IDEMPOTENCY_KEY_CONFLICT,
                "Idempotency Key Conflict",
                exception.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail businessRuleException(final BusinessRuleException exception) {

        return problemDetail(
                exception,
                INTERNAL_SERVER_ERROR,
                TYPE_BUSINESS_RULE_VIOLATION,
                "Business Rule Violation",
                exception.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(TechnicalProblemException.class)
    public ProblemDetail technicalProblemException(final TechnicalProblemException exception) {

        return problemDetail(
                exception,
                INTERNAL_SERVER_ERROR,
                TYPE_TECHNICAL_PROBLEM,
                "Technical Problem",
                exception.getMessage());
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail runtimeException(final RuntimeException exception) {

        return problemDetail(
                exception,
                INTERNAL_SERVER_ERROR,
                TYPE_INTERNAL_ERROR,
                "Internal Server Error",
                RUNTIME_EXCEPTION_ERROR_MESSAGE);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail responseStatusException(final ResponseStatusException exception) {

        final HttpStatusCode status = exception.getStatusCode();
        return problemDetail(exception, status, problemTypeFor(status), null, exception.getReason());
    }

    private ProblemDetail problemDetail(
            final Exception exception,
            final HttpStatusCode status,
            final URI type,
            final String title,
            final String detail) {

        final String errorId = UUID.randomUUID().toString();
        log.error("{} [{}]: {}", exception.getClass().getSimpleName(), errorId, exception.getMessage());

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(type);
        if (title != null) {

            problemDetail.setTitle(title);
        }
        problemDetail.setProperty(ERROR_ID_PROPERTY, errorId);
        return problemDetail;
    }

    private URI problemTypeFor(final HttpStatusCode status) {

        final HttpStatus resolved = HttpStatus.resolve(status.value());
        final String suffix = resolved != null ? resolved.name().toLowerCase(Locale.ROOT).replace('_', '-') : "error";
        return URI.create(PROBLEM_TYPE_PREFIX + suffix);
    }

}
