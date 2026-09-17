package com.cp.ecommerce.adapter.common.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.cp.ecommerce.adapter.common.exception.RateLimitExceededException;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RateLimitedExecutor}.
 */
class RateLimitedExecutorTest {

    private static final String INSTANCE_NAME = "testInstance";

    @Test
    void shouldReturnActionResultWhenPermitted() {

        final RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        final RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(rateLimiterRegistry);

        final String result = rateLimitedExecutor.callRateLimited(INSTANCE_NAME, () -> "success");

        assertThat(result).isEqualTo("success");
    }

    @Test
    void shouldRejectCallsExceedingTheLimit() {

        final RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        final RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(rateLimiterRegistry);
        final AtomicInteger invocations = new AtomicInteger();

        rateLimitedExecutor.callRateLimited(INSTANCE_NAME, invocations::incrementAndGet);

        assertThatThrownBy(() -> rateLimitedExecutor.callRateLimited(INSTANCE_NAME, invocations::incrementAndGet))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining(INSTANCE_NAME)
                .hasCauseInstanceOf(RequestNotPermitted.class)
                .extracting(exception -> ((RateLimitExceededException) exception).getRetryAfter())
                .isEqualTo(Duration.ofMinutes(1));
        assertThat(invocations.get()).isEqualTo(1);
    }

}
