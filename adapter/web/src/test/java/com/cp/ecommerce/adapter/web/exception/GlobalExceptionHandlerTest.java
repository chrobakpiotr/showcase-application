package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import com.cp.ecommerce.adapter.common.exception.BusinessRuleException;
import com.cp.ecommerce.adapter.common.exception.CartConflictException;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.adapter.common.exception.IdempotencyKeyConflictException;
import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.common.exception.OrderNotCancellableException;
import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.adapter.common.exception.RateLimitExceededException;
import com.cp.ecommerce.adapter.common.exception.StockLevelConflictException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

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
                BAD_REQUEST,
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
    void shouldHandleOrderNotCancellableException() {

        assertProblem(
                handler.orderNotCancellableException(new OrderNotCancellableException(EXCEPTION_MESSAGE)),
                CONFLICT,
                "Order Not Cancellable",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleInsufficientStockException() {

        assertProblem(
                handler.insufficientStockException(new InsufficientStockException(EXCEPTION_MESSAGE)),
                CONFLICT,
                "Insufficient Stock",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandlePaymentDeclinedException() {

        assertProblem(
                handler.paymentDeclinedException(new PaymentDeclinedException(EXCEPTION_MESSAGE)),
                PAYMENT_REQUIRED,
                "Payment Declined",
                EXCEPTION_MESSAGE);
    }

    @Test
    void shouldHandleStockLevelConflictException() {

        assertProblem(
                handler.stockLevelConflictException(new StockLevelConflictException("SKU-1", new IllegalStateException())),
                CONFLICT,
                "Stock Level Conflict",
                "Concurrent stock modification detected for SKU SKU-1, please retry");
    }

    @Test
    void shouldHandleCartConflictException() {

        assertProblem(
                handler.cartConflictException(new CartConflictException("CART-1", new IllegalStateException())),
                CONFLICT,
                "Cart Conflict",
                "Concurrent cart modification detected for cart CART-1, please retry");
    }

    @Test
    void shouldHandleRateLimitExceededException() {

        final ResponseEntity<ProblemDetail> response = handler
                .rateLimitExceededException(new RateLimitExceededException(EXCEPTION_MESSAGE, Duration.ofSeconds(5), null));

        assertThat(response.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertProblem(
                response.getBody(),
                TOO_MANY_REQUESTS,
                "Rate Limit Exceeded",
                "Too many requests, please retry after a short delay");
    }

    @Test
    void shouldClampRetryAfterHeaderToAtLeastOneSecond() {

        final ResponseEntity<ProblemDetail> response = handler
                .rateLimitExceededException(new RateLimitExceededException(EXCEPTION_MESSAGE, Duration.ofMillis(200), null));

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
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
