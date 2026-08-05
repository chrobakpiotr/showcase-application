package com.cp.ecommerce.adapter.security.configuration;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Test class checking rest template's timeout behavior.
 */
@SpringBootTest
class RestTemplateTimeoutTest {

    private static final String TEST_URL = "http://10.255.255.255";

    private static final Duration MAX_EXPECTED_DURATION = Duration.ofSeconds(20);

    @Autowired
    private transient RestTemplate restTemplate;

    @Test
    @Timeout(30)
    void shouldThrowTimeoutException() {

        final long start = System.nanoTime();
        final Throwable thrown = catchThrowable(() -> restTemplate.getForObject(TEST_URL, String.class));
        final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(thrown).isInstanceOf(ResourceAccessException.class);
        assertThat(elapsed).isLessThan(MAX_EXPECTED_DURATION);
    }
}
