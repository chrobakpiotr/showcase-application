package com.cp.ecommerce.domain.coupon.port.incoming;

import com.cp.ecommerce.domain.coupon.Coupon;

/**
 * Updates coupon lifecycle and mutable attributes.
 */
public interface ManageCouponInPort {

    Coupon updateCoupon(String code, Coupon coupon);

    Coupon activateCoupon(String code);

    Coupon deactivateCoupon(String code);

}
