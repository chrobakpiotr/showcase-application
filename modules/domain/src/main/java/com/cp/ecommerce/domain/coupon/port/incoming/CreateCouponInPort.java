package com.cp.ecommerce.domain.coupon.port.incoming;

import com.cp.ecommerce.domain.coupon.Coupon;

/**
 * Creates new coupons.
 */
public interface CreateCouponInPort {

    Coupon createCoupon(Coupon coupon);

}
