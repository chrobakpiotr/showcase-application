package com.cp.ecommerce.adapter.common.constant;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CacheConstants}.
 */
class CacheConstantsTest {

    @Test
    void shouldExposeExpectedCacheConstants() {

        assertThat(CacheConstants.ORDER_CACHE_NAME).isEqualTo("orderCache");
        assertThat(CacheConstants.ONE_HOUR_DURATION).isEqualTo(Duration.ofHours(1));
        assertThat(CacheConstants.ONE_HUNDRED_ENTRIES).isEqualTo(100L);
    }

}
