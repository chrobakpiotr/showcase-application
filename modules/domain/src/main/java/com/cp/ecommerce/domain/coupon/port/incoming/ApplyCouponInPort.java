package com.cp.ecommerce.domain.coupon.port.incoming;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.coupon.CouponDiscount;

/**
 * Applies a coupon and consumes one redemption.
 */
public interface ApplyCouponInPort {

    CouponDiscount applyCoupon(String code, BigDecimal orderTotal, Instant now);

}
