package com.cp.ecommerce.domain.coupon;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponMutationTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void shouldTreatMinimumAmountAsInclusiveBoundary() {
        final Coupon coupon = percentage().minimumOrderAmount(new BigDecimal("100.00")).build();

        assertThat(coupon.isValidFor(new BigDecimal("100.00"), NOW)).isTrue();
        assertThat(coupon.isValidFor(new BigDecimal("99.99"), NOW)).isFalse();
    }

    @Test
    void shouldTreatRedemptionLimitAsExclusiveOfExhaustedCount() {
        final Coupon available = percentage().maxRedemptions(2).redemptionCount(1).build();
        final Coupon exhausted = percentage().maxRedemptions(2).redemptionCount(2).build();

        assertThat(available.isValidFor(new BigDecimal("100.00"), NOW)).isTrue();
        assertThat(exhausted.isValidFor(new BigDecimal("100.00"), NOW)).isFalse();
    }

    @Test
    void shouldTreatExpiryInstantAsStillValidAndEarlierInstantAsExpired() {
        final Coupon expiresNow = percentage().expiresAt(NOW).build();
        final Coupon expired = percentage().expiresAt(NOW.minusNanos(1)).build();

        assertThat(expiresNow.isValidFor(new BigDecimal("100.00"), NOW)).isTrue();
        assertThat(expired.isValidFor(new BigDecimal("100.00"), NOW)).isFalse();
        assertThat(expiresNow.isValidFor(new BigDecimal("100.00"), null)).isTrue();
    }

    @Test
    void shouldRejectNullZeroAndNegativeOrderTotals() {
        final Coupon coupon = percentage().build();

        assertThat(coupon.isValidFor(null, NOW)).isFalse();
        assertThat(coupon.isValidFor(BigDecimal.ZERO, NOW)).isFalse();
        assertThat(coupon.isValidFor(new BigDecimal("-0.01"), NOW)).isFalse();

        assertThat(coupon.calculateDiscount(null)).isEqualByComparingTo("0");
        assertThat(coupon.calculateDiscount(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(coupon.calculateDiscount(new BigDecimal("-0.01"))).isEqualByComparingTo("0");
    }

    @Test
    void shouldRejectEveryInvalidDiscountConfigurationBoundary() {
        assertThat(percentage().discountValue(BigDecimal.ZERO).build().isValidFor(new BigDecimal("100"), NOW)).isFalse();
        assertThat(percentage().discountValue(new BigDecimal("-0.01")).build().isValidFor(new BigDecimal("100"), NOW))
                .isFalse();
        assertThat(percentage().discountValue(new BigDecimal("100.01")).build().isValidFor(new BigDecimal("100"), NOW))
                .isFalse();
        assertThat(percentage().minimumOrderAmount(new BigDecimal("-0.01")).build().isValidFor(new BigDecimal("100"), NOW))
                .isFalse();
        assertThat(percentage().maxRedemptions(0).build().isValidFor(new BigDecimal("100"), NOW)).isFalse();
    }

    @Test
    void shouldAllowExactHundredPercentButRejectMoreThanHundred() {
        final Coupon hundred = percentage().discountValue(new BigDecimal("100")).build();
        final Coupon overHundred = percentage().discountValue(new BigDecimal("100.01")).build();

        assertThat(hundred.isValidFor(new BigDecimal("37.13"), NOW)).isTrue();
        assertThat(hundred.calculateDiscount(new BigDecimal("37.13"))).isEqualByComparingTo("37.13");
        assertThat(overHundred.calculateDiscount(new BigDecimal("37.13"))).isEqualByComparingTo("0");
    }

    @Test
    void shouldPreserveAllBusinessFieldsWhenIncrementingRedemptionCount() {
        final Instant expiry = NOW.plusSeconds(3600);
        final Coupon original = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10.00"))
                .minimumOrderAmount(new BigDecimal("25.00"))
                .maxRedemptions(7)
                .redemptionCount(3)
                .expiresAt(expiry)
                .active(false)
                .version(9)
                .build();

        final Coupon incremented = original.incrementRedemptionCount();

        assertThat(incremented.getCode()).isEqualTo("SAVE10");
        assertThat(incremented.getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(incremented.getDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(incremented.getMinimumOrderAmount()).isEqualByComparingTo("25.00");
        assertThat(incremented.getMaxRedemptions()).isEqualTo(7);
        assertThat(incremented.getRedemptionCount()).isEqualTo(4);
        assertThat(incremented.getExpiresAt()).isEqualTo(expiry);
        assertThat(incremented.isActive()).isFalse();
        assertThat(incremented.getVersion()).isEqualTo(9);
    }

    private static Coupon.CouponBuilder percentage() {
        return Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .active(true);
    }
}
