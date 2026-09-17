package com.cp.ecommerce.domain.coupon.port.incoming;

import com.cp.ecommerce.domain.coupon.Coupon;

/**
 * Looks up a single coupon.
 */
public interface GetCouponInPort {

    Coupon getCoupon(String code);

}
