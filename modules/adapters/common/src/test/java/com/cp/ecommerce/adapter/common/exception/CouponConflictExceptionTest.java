package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CouponConflictException}.
 */
class CouponConflictExceptionTest {

    @Test
    void shouldBuildMessageFromCouponCodeAndCause() {

        final Throwable cause = new IllegalStateException("stale version");

        final CouponConflictException exception = new CouponConflictException("SAVE10", cause);

        assertThat(exception).hasMessage("Concurrent coupon modification detected for code SAVE10, please retry")
                .hasCause(cause)
                .isInstanceOf(BusinessRuleException.class);
    }

}
