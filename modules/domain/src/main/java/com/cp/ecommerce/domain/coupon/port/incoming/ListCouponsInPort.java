package com.cp.ecommerce.domain.coupon.port.incoming;

import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;

/**
 * Lists coupons.
 */
public interface ListCouponsInPort {

    PagedCoupons listCoupons(CouponPageQuery query);

}
