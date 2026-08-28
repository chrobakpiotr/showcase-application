package com.cp.ecommerce.adapter.common.exception;

import java.io.Serial;

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

    public RateLimitExceededException(final String message, final Throwable cause) {

        super(message, cause);
    }

}
