package com.cp.ecommerce.domain.coupon.port.outgoing;

import com.cp.ecommerce.domain.coupon.Coupon;

/**
 * Saves a coupon.
 */
public interface SaveCouponOutPort {

    Coupon save(Coupon coupon);

}
