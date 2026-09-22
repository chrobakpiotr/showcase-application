package com.cp.ecommerce.adapter.common.resilience;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

import com.cp.ecommerce.foundation.function.RuntimeFailureBoundary;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Executes outgoing adapter calls (e.g. AMQP, SMTP) behind a named circuit breaker and retry combination, so transient failures
 * of external systems don't immediately fail the calling use case.
 */
@Component
@RequiredArgsConstructor
public class ResilientExecutor {

    private final transient CircuitBreakerRegistry circuitBreakerRegistry;
    private final transient RetryRegistry retryRegistry;

    /**
     * Runs the given action behind a circuit breaker and retry, both looked up (and lazily created with default configuration)
     * by {@code instanceName}. Any unchecked exception thrown by the action (or resilience4j itself, e.g.
     * {@code CallNotPermittedException} when the circuit is open) propagates to the caller.
     */
    public void runResilient(final String instanceName, final Runnable action) {

        final CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        final Retry retry = retryRegistry.retry(instanceName);
        final Runnable decorated = Retry.decorateRunnable(retry, CircuitBreaker.decorateRunnable(circuitBreaker, action));
        decorated.run();
    }

    /**
     * Same as {@link #runResilient(String, Runnable)} but for actions that return a value and/or throw checked exceptions.
     */
    public <T> T callResilient(final String instanceName, final Callable<T> action) throws Exception {

        final CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        final Retry retry = retryRegistry.retry(instanceName);
        final Callable<T> decorated = Retry.decorateCallable(retry, CircuitBreaker.decorateCallable(circuitBreaker, action));
        return decorated.call();
    }

    /**
     * Executes an unchecked resilient action and maps any runtime failure to an explicit fallback value.
     *
     * <p>
     * The action runs synchronously on the calling thread. {@link Error}s are never converted to fallback values.
     * </p>
     */
    public <T> T callResilientOrElse(
            final String instanceName,
            final Supplier<T> action,
            final Function<RuntimeException, T> fallback) {

        final CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        final Retry retry = retryRegistry.retry(instanceName);
        final Supplier<T> decorated = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, action));
        return RuntimeFailureBoundary.call(decorated, fallback);
    }

}
