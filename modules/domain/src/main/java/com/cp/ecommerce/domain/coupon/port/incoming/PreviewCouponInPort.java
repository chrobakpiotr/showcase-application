package com.cp.ecommerce.domain.coupon.port.incoming;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.coupon.CouponDiscount;

/**
 * Validates a coupon without consuming a redemption.
 */
public interface PreviewCouponInPort {

    CouponDiscount previewCoupon(String code, BigDecimal orderTotal, Instant now);

}
