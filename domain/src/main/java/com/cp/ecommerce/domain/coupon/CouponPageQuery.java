package com.cp.ecommerce.domain.coupon;

/**
 * Page request for listing coupons.
 */
public record CouponPageQuery(int page, int size, Boolean activeOnly) {

    public static final int DEFAULT_SIZE = 10;

    public static final int MAX_SIZE = 100;

}
