package com.cp.ecommerce.adapter.web.cart.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for attaching a coupon to a cart.
 */
public record ApplyCouponResource(@Schema(example = "SAVE10") String code) {

}
