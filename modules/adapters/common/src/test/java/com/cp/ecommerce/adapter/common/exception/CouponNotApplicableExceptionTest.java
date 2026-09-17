package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CouponNotApplicableException}.
 */
class CouponNotApplicableExceptionTest {

    @Test
    void shouldBuildMessageFromCouponCodeAndReason() {

        final CouponNotApplicableException exception = new CouponNotApplicableException("SAVE10", "expired");

        assertThat(exception).hasMessage("Coupon SAVE10 cannot be applied: expired").isInstanceOf(BusinessRuleException.class);
    }

}
