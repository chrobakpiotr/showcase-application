package com.cp.ecommerce.adapter.common.utils;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.DiscountType;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CouponBuilder {

    public static final String TEST_COUPON_CODE = "SAVE10";

    public static Coupon mockCoupon() {

        return Coupon.builder()
                .code(TEST_COUPON_CODE)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .minimumOrderAmount(new BigDecimal("50.00"))
                .maxRedemptions(100)
                .redemptionCount(2)
                .expiresAt(new Date(System.currentTimeMillis() + 86400000))
                .active(true)
                .build();
    }

}
