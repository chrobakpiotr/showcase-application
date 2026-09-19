package com.cp.ecommerce.adapter.common.time;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyDateInteropTest {

    @Test
    void shouldConvertInstantToLegacyDate() {

        final Instant instant = Instant.parse("2026-09-19T12:00:00.123Z");

        final var result = LegacyDateInterop.toDate(instant);

        assertThat(result.toInstant()).isEqualTo(instant);
    }

}
