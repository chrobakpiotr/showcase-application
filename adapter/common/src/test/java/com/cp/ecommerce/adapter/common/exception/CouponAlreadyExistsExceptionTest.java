package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CouponAlreadyExistsException}.
 */
class CouponAlreadyExistsExceptionTest {

    @Test
    void shouldBuildMessageFromCouponCode() {

        final CouponAlreadyExistsException exception = new CouponAlreadyExistsException("SAVE10");

        assertThat(exception).hasMessage("Coupon SAVE10 already exists").isInstanceOf(BusinessRuleException.class);
    }

}
