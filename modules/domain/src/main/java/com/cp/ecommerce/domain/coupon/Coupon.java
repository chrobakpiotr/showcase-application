package com.cp.ecommerce.domain.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Back-office-managed coupon identified by a human-chosen business code rather than a generated id.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Coupon extends ValidDomainObject<Coupon> {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @NotBlank(message = ValidationConstants.INVALID_COUPON_CODE)
    @Size(max = ValidationConstants.COUPON_CODE_MAX, message = ValidationConstants.INVALID_COUPON_CODE)
    @Pattern(regexp = "^[A-Z0-9-]{3,30}$", message = ValidationConstants.INVALID_COUPON_CODE)
    String code;

    @NotNull(message = ValidationConstants.INVALID_COUPON_DISCOUNT_TYPE)
    DiscountType discountType;

    @NotNull(message = ValidationConstants.INVALID_COUPON_DISCOUNT_VALUE)
    BigDecimal discountValue;

    BigDecimal minimumOrderAmount;

    Integer maxRedemptions;

    @Builder.Default
    @Min(value = 0, message = ValidationConstants.INVALID_COUPON_REDEMPTION_COUNT)
    int redemptionCount = 0;

    Instant expiresAt;

    @Builder.Default
    boolean active = true;

    @Builder.Default
    long version = 0;

    public static Coupon.CouponBuilder builder() {

        return new Coupon.CouponBuilder() {

            @Override
            public Coupon build() {

                return super.build().validate();
            }
        };
    }

    /**
     * Whether the coupon is currently applicable for the supplied subtotal.
     */
    public boolean isValidFor(final BigDecimal orderTotal, final Instant now) {

        if (!active || orderTotal == null || orderTotal.signum() <= 0 || hasInvalidDiscountConfiguration()) {

            return false;
        }
        if (minimumOrderAmount != null && orderTotal.compareTo(minimumOrderAmount) < 0) {

            return false;
        }
        if (maxRedemptions != null && redemptionCount >= maxRedemptions) {

            return false;
        }
        return expiresAt == null || now == null || !expiresAt.isBefore(now);
    }

    /**
     * Calculates the monetary discount for the supplied subtotal, clamped so the final total never becomes negative.
     */
    public BigDecimal calculateDiscount(final BigDecimal orderTotal) {

        if (orderTotal == null || orderTotal.signum() != 1 || hasInvalidDiscountConfiguration()) {

            return BigDecimal.ZERO;
        }
        final BigDecimal rawDiscount = switch (discountType) {
        case PERCENTAGE -> orderTotal.multiply(discountValue).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        case FIXED_AMOUNT -> discountValue;
        };
        return rawDiscount.max(BigDecimal.ZERO).min(orderTotal).setScale(2, RoundingMode.HALF_UP);
    }

    public Coupon incrementRedemptionCount() {

        return Coupon.builder()
                .code(code)
                .discountType(discountType)
                .discountValue(discountValue)
                .minimumOrderAmount(minimumOrderAmount)
                .maxRedemptions(maxRedemptions)
                .redemptionCount(redemptionCount + 1)
                .expiresAt(expiresAt)
                .active(active)
                .version(version)
                .build();
    }

    private boolean hasInvalidDiscountConfiguration() {

        return discountType == null || discountValue == null || discountValue.signum() <= 0
                || minimumOrderAmount != null && minimumOrderAmount.signum() < 0 || maxRedemptions != null && maxRedemptions < 1
                || discountType == DiscountType.PERCENTAGE && discountValue.compareTo(HUNDRED) > 0;
    }

}
