package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;
import java.util.Locale;

import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.BusinessRuleException;
import com.cp.ecommerce.foundation.exception.CartConflictException;
import com.cp.ecommerce.foundation.exception.CouponAlreadyExistsException;
import com.cp.ecommerce.foundation.exception.CouponConflictException;
import com.cp.ecommerce.foundation.exception.CouponNotApplicableException;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;
import com.cp.ecommerce.foundation.exception.IdempotencyKeyConflictException;
import com.cp.ecommerce.foundation.exception.InsufficientStockException;
import com.cp.ecommerce.foundation.exception.OrderNotCancellableException;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;
import com.cp.ecommerce.foundation.exception.RateLimitExceededException;
import com.cp.ecommerce.foundation.exception.StockLevelConflictException;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.tracing.Tracer;
import jakarta.validation.ConstraintViolationException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/**
 * Class serving exception handling functionality.
 *
 * <p>
 * Every response follows <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a> ("Problem Details for HTTP APIs"):
 * Spring serializes a returned {@link ProblemDetail} as {@code application/problem+json} with the standard
 * {@code type}/{@code title}/{@code status}/{@code detail} members, so clients get a machine-readable, self-describing error
 * shape instead of an ad-hoc one. An {@code errorId} extension member is added to every response and also written to the server
 * log. When an active Micrometer span exists, its {@code traceId} is exposed as a second extension member and logged alongside
 * the error ID, linking the client-visible failure to the distributed trace without inventing a trace outside an observed
 * request.
 */
@RestControllerAdvice(annotations = Component.class)
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class GlobalExceptionHandler extends ReturnAndShipmentExceptionHandlerSupport {

    public static final String RUNTIME_EXCEPTION_ERROR_MESSAGE = "Could not process your request";

    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:";

    private static final URI TYPE_CONSTRAINT_VIOLATION = URI.create(PROBLEM_TYPE_PREFIX + "constraint-violation");
    private static final URI TYPE_DOMAIN_VALIDATION_ERROR = URI.create(PROBLEM_TYPE_PREFIX + "domain-validation-error");
    private static final URI TYPE_BUSINESS_RULE_VIOLATION = URI.create(PROBLEM_TYPE_PREFIX + "business-rule-violation");
    private static final URI TYPE_IDEMPOTENCY_KEY_CONFLICT = URI.create(PROBLEM_TYPE_PREFIX + "idempotency-key-conflict");
    private static final URI TYPE_ORDER_NOT_CANCELLABLE = URI.create(PROBLEM_TYPE_PREFIX + "order-not-cancellable");
    private static final URI TYPE_INSUFFICIENT_STOCK = URI.create(PROBLEM_TYPE_PREFIX + "insufficient-stock");
    private static final URI TYPE_STOCK_LEVEL_CONFLICT = URI.create(PROBLEM_TYPE_PREFIX + "stock-level-conflict");
    private static final URI TYPE_CART_CONFLICT = URI.create(PROBLEM_TYPE_PREFIX + "cart-conflict");
    private static final URI TYPE_COUPON_CONFLICT = URI.create(PROBLEM_TYPE_PREFIX + "coupon-conflict");
    private static final URI TYPE_COUPON_NOT_APPLICABLE = URI.create(PROBLEM_TYPE_PREFIX + "coupon-not-applicable");
    private static final URI TYPE_COUPON_ALREADY_EXISTS = URI.create(PROBLEM_TYPE_PREFIX + "coupon-already-exists");
    private static final URI TYPE_PAYMENT_DECLINED = URI.create(PROBLEM_TYPE_PREFIX + "payment-declined");
    private static final URI TYPE_PAYMENT_REFUND_CONFLICT = URI.create(PROBLEM_TYPE_PREFIX + "payment-refund-conflict");
    private static final URI TYPE_TECHNICAL_PROBLEM = URI.create(PROBLEM_TYPE_PREFIX + "technical-problem");
    private static final URI TYPE_RATE_LIMIT_EXCEEDED = URI.create(PROBLEM_TYPE_PREFIX + "rate-limit-exceeded");
    private static final URI TYPE_INTERNAL_ERROR = URI.create(PROBLEM_TYPE_PREFIX + "internal-error");

    public GlobalExceptionHandler(final ObjectProvider<Tracer> tracerProvider) {

        super(tracerProvider);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(ApplicationBadRequestException.class)
    public ProblemDetail applicationBadRequestException(final ApplicationBadRequestException exception) {

        return problemDetail(exception, BAD_REQUEST, TYPE_BUSINESS_RULE_VIOLATION, "Bad Request", exception.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ApplicationNotFoundException.class)
    public ProblemDetail applicationNotFoundException(final ApplicationNotFoundException exception) {

        return problemDetail(
                exception,
                HttpStatus.NOT_FOUND,
                TYPE_BUSINESS_RULE_VIOLATION,
                "Resource Not Found",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler({ ApplicationConflictException.class, PaymentOperationConflictException.class })
    public ProblemDetail applicationConflictException(final BusinessRuleException exception) {

        return problemDetail(exception, CONFLICT, TYPE_BUSINESS_RULE_VIOLATION, "Conflict", exception.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolationException(final ConstraintViolationException exception) {

        return problemDetail(exception, BAD_REQUEST, TYPE_CONSTRAINT_VIOLATION, "Constraint Violation", exception.getMessage());
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(DomainObjectValidationException.class)
    public ProblemDetail domainObjectValidationException(final DomainObjectValidationException exception) {

        return problemDetail(
                exception,
                BAD_REQUEST,
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

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> rateLimitExceededException(final RateLimitExceededException exception) {

        final ProblemDetail problemDetail = problemDetail(
                exception,
                TOO_MANY_REQUESTS,
                TYPE_RATE_LIMIT_EXCEEDED,
                "Rate Limit Exceeded",
                "Too many requests, please retry after a short delay");
        final long retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(problemDetail);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(OrderNotCancellableException.class)
    public ProblemDetail orderNotCancellableException(final OrderNotCancellableException exception) {

        return problemDetail(exception, CONFLICT, TYPE_ORDER_NOT_CANCELLABLE, "Order Not Cancellable", exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail insufficientStockException(final InsufficientStockException exception) {

        return problemDetail(exception, CONFLICT, TYPE_INSUFFICIENT_STOCK, "Insufficient Stock", exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(StockLevelConflictException.class)
    public ProblemDetail stockLevelConflictException(final StockLevelConflictException exception) {

        return problemDetail(exception, CONFLICT, TYPE_STOCK_LEVEL_CONFLICT, "Stock Level Conflict", exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(CartConflictException.class)
    public ProblemDetail cartConflictException(final CartConflictException exception) {

        return problemDetail(exception, CONFLICT, TYPE_CART_CONFLICT, "Cart Conflict", exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(CouponConflictException.class)
    public ProblemDetail couponConflictException(final CouponConflictException exception) {

        return problemDetail(exception, CONFLICT, TYPE_COUPON_CONFLICT, "Coupon Conflict", exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(CouponNotApplicableException.class)
    public ProblemDetail couponNotApplicableException(final CouponNotApplicableException exception) {

        return problemDetail(exception, CONFLICT, TYPE_COUPON_NOT_APPLICABLE, "Coupon Not Applicable", exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(CouponAlreadyExistsException.class)
    public ProblemDetail couponAlreadyExistsException(final CouponAlreadyExistsException exception) {

        return problemDetail(exception, CONFLICT, TYPE_COUPON_ALREADY_EXISTS, "Coupon Already Exists", exception.getMessage());
    }

    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail paymentDeclinedException(final PaymentDeclinedException exception) {

        return problemDetail(
                exception,
                HttpStatus.PAYMENT_REQUIRED,
                TYPE_PAYMENT_DECLINED,
                "Payment Declined",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(PaymentRefundConflictException.class)
    public ProblemDetail paymentRefundConflictException(final PaymentRefundConflictException exception) {

        return problemDetail(
                exception,
                CONFLICT,
                TYPE_PAYMENT_REFUND_CONFLICT,
                "Payment Refund Conflict",
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

    private URI problemTypeFor(final HttpStatusCode status) {

        final HttpStatus resolved = HttpStatus.resolve(status.value());
        final String suffix = resolved != null ? resolved.name().toLowerCase(Locale.ROOT).replace('_', '-') : "error";
        return URI.create(PROBLEM_TYPE_PREFIX + suffix);
    }

}
