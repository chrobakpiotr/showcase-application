package com.cp.ecommerce.domain.coupon;

import java.math.BigDecimal;

/**
 * Result of validating/applying a coupon to an order total.
 *
 * @param code applied coupon code
 * @param discountAmount discount amount to subtract from the subtotal
 */
public record CouponDiscount(String code, BigDecimal discountAmount) {

}
