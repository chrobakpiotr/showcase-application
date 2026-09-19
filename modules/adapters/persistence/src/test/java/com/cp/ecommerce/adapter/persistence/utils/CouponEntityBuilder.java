package com.cp.ecommerce.adapter.persistence.utils;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.domain.coupon.DiscountType;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.CouponBuilder.TEST_COUPON_CODE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CouponEntityBuilder {

    public static CouponEntity mockCouponEntity() {

        return CouponEntity.builder()
                .code(TEST_COUPON_CODE)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .minimumOrderAmount(new BigDecimal("50.00"))
                .maxRedemptions(100)
                .redemptionCount(2)
                .expiresAt(Instant.ofEpochMilli(System.currentTimeMillis() + 86400000))
                .active(true)
                .version(3)
                .build();
    }

}
