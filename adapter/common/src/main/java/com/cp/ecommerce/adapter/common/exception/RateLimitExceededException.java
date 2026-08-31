package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;
import java.time.Duration;

/**
 * Exception thrown when a named rate limiter rejects a call because too many requests were made in the current time window.
 *
 * <p>
 * Kept adapter-agnostic on purpose: callers of {@code RateLimitedExecutor} (e.g. web controllers) only ever see this exception,
 * never resilience4j's own {@code RequestNotPermitted}, so the specific resilience library backing rate limiting stays an
 * implementation detail of {@code adapter:common}.
 * </p>
 */
public class RateLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public RateLimitExceededException(final String message, final Duration retryAfter, final Throwable cause) {

        super(message, cause);
        this.retryAfter = retryAfter;
    }

    /**
     * How long the caller should wait before a fresh permit is expected to become available again, i.e. the rejecting rate
     * limiter's configured refresh period. Suitable for populating an HTTP {@code Retry-After} response header.
     */
    public Duration getRetryAfter() {

        return retryAfter;
    }

}
