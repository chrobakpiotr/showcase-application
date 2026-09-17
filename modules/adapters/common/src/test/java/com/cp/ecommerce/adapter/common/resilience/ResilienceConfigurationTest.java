package com.cp.ecommerce.adapter.common.resilience;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResilienceConfiguration}.
 */
class ResilienceConfigurationTest {

    private static final String INSTANCE_NAME = "someInstance";

    private final transient ResilienceConfiguration configuration = new ResilienceConfiguration();

    @Test
    void shouldCreateCircuitBreakerRegistry() {

        final CircuitBreakerRegistry registry = configuration.circuitBreakerRegistry();

        assertThat(registry).isNotNull();
        assertThat(registry.circuitBreaker(INSTANCE_NAME)).isNotNull();
    }

    @Test
    void shouldCreateRetryRegistryWithExponentialJitteredBackoff() {

        final RetryRegistry registry = configuration.retryRegistry();
        final RetryConfig retryConfig = registry.retry(INSTANCE_NAME).getRetryConfig();

        assertThat(retryConfig.getMaxAttempts()).isEqualTo(3);

        final Either<Throwable, Object> failure = Either.left(new IllegalStateException("transient failure"));
        final long firstRetryDelay = retryConfig.<Object> getIntervalBiFunction().apply(1, failure);
        final long secondRetryDelay = retryConfig.<Object> getIntervalBiFunction().apply(2, failure);

        assertThat(firstRetryDelay).isBetween(375L, 625L);
        assertThat(secondRetryDelay).isBetween(750L, 1_250L);
        assertThat(secondRetryDelay).isGreaterThan(firstRetryDelay);
    }

    @Test
    void shouldCreateRateLimiterRegistry() {

        final RateLimiterRegistry registry = configuration.rateLimiterRegistry();

        assertThat(registry).isNotNull();
        assertThat(registry.rateLimiter(INSTANCE_NAME)).isNotNull();
    }

    @Test
    void shouldBindMetricsWhenMeterRegistryIsAvailable() {

        final CircuitBreakerRegistry circuitBreakerRegistry = configuration.circuitBreakerRegistry();
        final RetryRegistry retryRegistry = configuration.retryRegistry();
        final RateLimiterRegistry rateLimiterRegistry = configuration.rateLimiterRegistry();
        circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
        retryRegistry.retry(INSTANCE_NAME);
        rateLimiterRegistry.rateLimiter(INSTANCE_NAME);
        final MeterBinder meterBinder = configuration
                .resilience4jMeterBinder(circuitBreakerRegistry, retryRegistry, rateLimiterRegistry);
        final MeterRegistry meterRegistry = new SimpleMeterRegistry();

        meterBinder.bindTo(meterRegistry);

        assertThat(meterRegistry.getMeters()).isNotEmpty();
    }

}
