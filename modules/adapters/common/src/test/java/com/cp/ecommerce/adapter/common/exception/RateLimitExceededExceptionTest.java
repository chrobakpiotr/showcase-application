package com.cp.ecommerce.adapter.common.exception;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitExceededExceptionTest {

    @Test
    void shouldExposeMessageCauseAndRetryAfter() {

        final Duration retryAfter = Duration.ofSeconds(2);
        final RuntimeException cause = new RuntimeException("rate limiter rejected");

        final RateLimitExceededException exception = new RateLimitExceededException("too many requests", retryAfter, cause);

        assertThat(exception).hasMessage("too many requests").hasCause(cause);
        assertThat(exception.getRetryAfter()).isEqualTo(retryAfter);
    }
}
