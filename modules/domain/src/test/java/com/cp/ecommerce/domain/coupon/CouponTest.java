package com.cp.ecommerce.domain.coupon;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponTest {

    @Test
    void shouldValidateActiveCouponForEligibleOrder() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .active(true)
                .build();
        assertThat(coupon.isValidFor(new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()))).isTrue();
    }

    @Test
    void shouldRejectExpiredCoupon() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .expiresAt(Instant.ofEpochMilli(1))
                .build();
        assertThat(coupon.isValidFor(new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()))).isFalse();
    }

    @Test
    void shouldRejectInactiveCoupon() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .active(false)
                .build();
        assertThat(coupon.isValidFor(new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()))).isFalse();
    }

    @Test
    void shouldRejectExhaustedCoupon() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .maxRedemptions(1)
                .redemptionCount(1)
                .build();
        assertThat(coupon.isValidFor(new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()))).isFalse();
    }

    @Test
    void shouldRejectOrderBelowMinimumAmount() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .minimumOrderAmount(new BigDecimal("200.00"))
                .build();
        assertThat(coupon.isValidFor(new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()))).isFalse();
    }

    @Test
    void shouldCalculatePercentageDiscount() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();
        assertThat(coupon.calculateDiscount(new BigDecimal("100.00"))).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldCalculateHundredPercentDiscount() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE100")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("100"))
                .build();
        assertThat(coupon.calculateDiscount(new BigDecimal("100.00"))).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldReturnZeroDiscountForInvalidConfiguration() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE100")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.ZERO)
                .build();
        assertThat(coupon.calculateDiscount(new BigDecimal("100.00"))).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldClampFixedDiscountAtOrderTotal() {

        final Coupon coupon = Coupon.builder()
                .code("TAKE200")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("200.00"))
                .build();
        assertThat(coupon.calculateDiscount(new BigDecimal("100.00"))).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldIncrementRedemptionCount() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .redemptionCount(2)
                .version(4)
                .build();
        assertThat(coupon.incrementRedemptionCount().getRedemptionCount()).isEqualTo(3);
        assertThat(coupon.incrementRedemptionCount().getVersion()).isEqualTo(4);
    }
}
