package com.cp.ecommerce.domain.coupon;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponMutationWave2Test {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void shouldValidateRequiredDiscountFields() {
        final Coupon missingType = Coupon.builder().code("SAVE10").discountValue(BigDecimal.TEN).build();
        final Coupon missingValue = Coupon.builder().code("SAVE10").discountType(DiscountType.PERCENTAGE).build();

        assertThatThrownBy(missingType::assertValidationsEmpty).isInstanceOf(DomainObjectValidationException.class);
        assertThatThrownBy(missingValue::assertValidationsEmpty).isInstanceOf(DomainObjectValidationException.class);
    }

    @Test
    void shouldCalculateNormalFixedDiscountWithoutClamping() {
        final Coupon coupon = Coupon.builder()
                .code("FIXED10")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10.00"))
                .build();

        assertThat(coupon.isValidFor(new BigDecimal("25.00"), NOW)).isTrue();
        assertThat(coupon.calculateDiscount(new BigDecimal("25.00"))).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRoundPercentageDiscountHalfUpToTwoDecimals() {
        final Coupon coupon = Coupon.builder()
                .code("SAVE15")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15"))
                .build();

        assertThat(coupon.calculateDiscount(new BigDecimal("33.33"))).isEqualByComparingTo("5.00");
    }

    @Test
    void shouldAllowAbsentOptionalConstraints() {
        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .minimumOrderAmount(null)
                .maxRedemptions(null)
                .expiresAt(null)
                .build();

        assertThat(coupon.isValidFor(new BigDecimal("1.00"), NOW)).isTrue();
        assertThat(coupon.isValidFor(new BigDecimal("1.00"), null)).isTrue();
    }
}
