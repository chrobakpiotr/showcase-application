package com.cp.ecommerce.adapter.common.resilience;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Provides the default {@link CircuitBreakerRegistry} and {@link RetryRegistry} used to make outgoing adapter calls (e.g. AMQP,
 * SMTP) resilient to transient failures, and the default {@link RateLimiterRegistry} used to protect incoming entrypoints (e.g.
 * REST endpoints) from being overwhelmed by request bursts.
 *
 * <p>
 * The registries are built by hand instead of relying on resilience4j's Spring Boot autoconfiguration starter, keeping this
 * module free of Spring Boot autoconfigure coupling.
 * </p>
 */
@Configuration
public class ResilienceConfiguration {

    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final float FAILURE_RATE_THRESHOLD = 50f;
    private static final Duration WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(10);
    private static final int PERMITTED_CALLS_IN_HALF_OPEN_STATE = 3;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_WAIT_DURATION = Duration.ofMillis(500);

    private static final int RATE_LIMIT_FOR_PERIOD = 20;
    private static final Duration RATE_LIMIT_REFRESH_PERIOD = Duration.ofSeconds(1);
    // Fail fast instead of parking the calling (possibly virtual) thread: a rejected request should turn into an
    // immediate 429 response, not add latency while waiting for a permit that may never come.
    private static final Duration RATE_LIMITER_TIMEOUT = Duration.ZERO;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {

        final CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .waitDurationInOpenState(WAIT_DURATION_IN_OPEN_STATE)
                .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN_STATE)
                .build();
        return CircuitBreakerRegistry.of(defaultConfig);
    }

    @Bean
    public RetryRegistry retryRegistry() {

        final RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(MAX_RETRY_ATTEMPTS)
                .waitDuration(RETRY_WAIT_DURATION)
                .build();
        return RetryRegistry.of(defaultConfig);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {

        final RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(RATE_LIMIT_FOR_PERIOD)
                .limitRefreshPeriod(RATE_LIMIT_REFRESH_PERIOD)
                .timeoutDuration(RATE_LIMITER_TIMEOUT)
                .build();
        return RateLimiterRegistry.of(defaultConfig);
    }

    /**
     * Binds circuit breaker, retry and rate limiter metrics to Micrometer only when a {@link MeterRegistry} bean is present in
     * the context (e.g. the assembled application), so that modules whose isolated tests don't expose metrics infrastructure
     * aren't forced to provide one.
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public MeterBinder resilience4jMeterBinder(
            final CircuitBreakerRegistry circuitBreakerRegistry,
            final RetryRegistry retryRegistry,
            final RateLimiterRegistry rateLimiterRegistry) {

        return meterRegistry -> {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);
            TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
            TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry).bindTo(meterRegistry);
        };
    }

}
