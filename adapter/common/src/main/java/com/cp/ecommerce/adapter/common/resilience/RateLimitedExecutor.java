package com.cp.ecommerce.adapter.common.resilience;

import java.util.function.Supplier;

import com.cp.ecommerce.adapter.common.exception.RateLimitExceededException;

import org.springframework.stereotype.Component;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;

/**
 * Protects an incoming entrypoint (e.g. a REST endpoint) with a named rate limiter, so a burst of requests is throttled instead
 * of being allowed to overwhelm the use case/downstream adapters it drives.
 *
 * <p>
 * Complements {@link ResilientExecutor}, which protects outgoing adapter calls: this class is for the inbound side of the
 * request instead, so it decorates a plain {@link Supplier} (the use cases it protects don't throw checked exceptions) rather
 * than a {@link java.util.concurrent.Callable}.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RateLimitedExecutor {

    private final transient RateLimiterRegistry rateLimiterRegistry;

    /**
     * Runs the given action behind a named rate limiter, looked up (and lazily created with default configuration) by
     * {@code instanceName}. Translates resilience4j's {@link RequestNotPermitted} into a {@link RateLimitExceededException} so
     * callers don't need a direct dependency on resilience4j to handle the rejection.
     */
    public <T> T callRateLimited(final String instanceName, final Supplier<T> action) {

        final RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(instanceName);
        try {

            return RateLimiter.decorateSupplier(rateLimiter, action).get();
        } catch (final RequestNotPermitted exception) {

            throw new RateLimitExceededException("Rate limit exceeded for '" + instanceName + "'", exception);
        }
    }

}
