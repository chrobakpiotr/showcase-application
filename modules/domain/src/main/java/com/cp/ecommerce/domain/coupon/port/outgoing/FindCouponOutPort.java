package com.cp.ecommerce.domain.coupon.port.outgoing;

import com.cp.ecommerce.domain.coupon.Coupon;

/**
 * Finds a coupon by code.
 */
public interface FindCouponOutPort {

    Coupon find(String code);

}
