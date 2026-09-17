package com.cp.ecommerce.adapter.common.resilience;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ResilientExecutor}.
 */
class ResilientExecutorTest {

    private static final String INSTANCE_NAME = "testInstance";

    @Test
    void shouldRetryRunnableUntilItSucceeds() {

        final RetryRegistry retryRegistry = RetryRegistry
                .of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        final ResilientExecutor resilientExecutor = new ResilientExecutor(circuitBreakerRegistry, retryRegistry);
        final AtomicInteger attempts = new AtomicInteger();

        resilientExecutor.runResilient(INSTANCE_NAME, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("transient failure");
            }
        });

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldRetryCallableUntilItSucceeds() throws Exception {

        final RetryRegistry retryRegistry = RetryRegistry
                .of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        final ResilientExecutor resilientExecutor = new ResilientExecutor(circuitBreakerRegistry, retryRegistry);
        final AtomicInteger attempts = new AtomicInteger();
        final Callable<String> action = () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("transient failure");
            }
            return "success";
        };

        final String result = resilientExecutor.callResilient(INSTANCE_NAME, action);

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldRejectCallsWhenCircuitBreakerIsOpen() {

        final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .build());
        final RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
        final ResilientExecutor resilientExecutor = new ResilientExecutor(circuitBreakerRegistry, retryRegistry);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> resilientExecutor.runResilient(INSTANCE_NAME, () -> {
                throw new IllegalStateException("failure");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThatThrownBy(() -> resilientExecutor.runResilient(INSTANCE_NAME, () -> {
        })).isInstanceOf(CallNotPermittedException.class);
    }

}
