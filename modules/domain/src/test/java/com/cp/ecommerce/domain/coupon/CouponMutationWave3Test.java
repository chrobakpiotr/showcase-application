package com.cp.ecommerce.domain.coupon;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponMutationWave3Test {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void shouldReturnCanonicalZeroDiscountAtExactZeroOrderTotalBoundary() {
        final Coupon coupon = percentage("10");

        final BigDecimal discount = coupon.calculateDiscount(BigDecimal.ZERO);

        assertThat(discount).isEqualTo(BigDecimal.ZERO);
        assertThat(discount.signum()).isZero();
        assertThat(discount.scale()).isZero();
    }

    @Test
    void shouldAllowExactZeroMinimumOrderAmount() {
        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .minimumOrderAmount(BigDecimal.ZERO)
                .build();

        assertThat(coupon.isValidFor(BigDecimal.ONE, NOW)).isTrue();
    }

    @Test
    void shouldAllowExactlyOneRedemptionWhenNoneHasBeenUsed() {
        final Coupon coupon = Coupon.builder()
                .code("ONCE")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.ONE)
                .maxRedemptions(1)
                .redemptionCount(0)
                .build();

        assertThat(coupon.isValidFor(BigDecimal.TEN, NOW)).isTrue();
    }

    @Test
    void shouldAcceptExactlyHundredPercentButRejectAnythingAboveHundred() {
        final Coupon exactHundred = percentage("100");
        final Coupon aboveHundred = percentage("100.01");

        assertThat(exactHundred.isValidFor(BigDecimal.TEN, NOW)).isTrue();
        assertThat(exactHundred.calculateDiscount(BigDecimal.TEN)).isEqualByComparingTo("10.00");

        assertThat(aboveHundred.isValidFor(BigDecimal.TEN, NOW)).isFalse();

        final BigDecimal invalidDiscount = aboveHundred.calculateDiscount(BigDecimal.TEN);
        assertThat(invalidDiscount).isEqualTo(BigDecimal.ZERO);
        assertThat(invalidDiscount.signum()).isZero();
        assertThat(invalidDiscount.scale()).isZero();
    }

    @Test
    void shouldExposePercentageUpperBoundaryIndependentlyFromOtherOptionalConstraints() {
        final Coupon exactHundred = Coupon.builder()
                .code("MAX100")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("100"))
                .minimumOrderAmount(null)
                .maxRedemptions(null)
                .expiresAt(null)
                .active(true)
                .build();

        final Coupon overHundred = Coupon.builder()
                .code("OVER100")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("100.0001"))
                .minimumOrderAmount(null)
                .maxRedemptions(null)
                .expiresAt(null)
                .active(true)
                .build();

        assertThat(exactHundred.isValidFor(new BigDecimal("1.00"), NOW)).isTrue();
        assertThat(overHundred.isValidFor(new BigDecimal("1.00"), NOW)).isFalse();
    }

    private static Coupon percentage(final String discount) {
        return Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal(discount))
                .build();
    }
}
