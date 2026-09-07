package com.cp.ecommerce.domain.coupon.port.outgoing;

import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;

/**
 * Lists persisted coupons.
 */
public interface FindCouponsOutPort {

    PagedCoupons findCoupons(CouponPageQuery query);

}
