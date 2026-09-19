package com.cp.ecommerce.adapter.common.time;

import java.time.Instant;
import java.util.Date;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Explicit compatibility bridge for third-party APIs that still require {@link Date}.
 *
 * <p>
 * The application time model is {@link Instant}; usages of this bridge stay at external library boundaries.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LegacyDateInterop {

    public static Date toDate(final Instant instant) {

        return Date.from(instant);
    }

}
