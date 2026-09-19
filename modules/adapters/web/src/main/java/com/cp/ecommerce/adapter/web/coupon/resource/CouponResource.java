package com.cp.ecommerce.adapter.web.coupon.resource;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.coupon.DiscountType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Request body for coupon creation/update.
 */
@Builder
public record CouponResource(@Schema(example = "SAVE10") String code, @Schema(example = "PERCENTAGE") DiscountType discountType,
        @Schema(example = "10.00") BigDecimal discountValue, @Schema(example = "50.00") BigDecimal minimumOrderAmount,
        @Schema(example = "100") Integer maxRedemptions, @Schema(example = "2026-12-31T23:59:59.000Z") Instant expiresAt,
        @Schema(example = "true") Boolean active) {

}
