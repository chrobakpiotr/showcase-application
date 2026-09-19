package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.port.incoming.ApplyCouponInPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;
import com.cp.ecommerce.foundation.exception.CouponConflictException;
import com.cp.ecommerce.foundation.exception.CouponNotApplicableException;

import lombok.RequiredArgsConstructor;

/**
 * Use case for consuming coupon redemptions with a bounded optimistic-lock retry loop.
 */
@UseCase
@RequiredArgsConstructor
public class ApplyCouponUseCase implements ApplyCouponInPort {

    private static final int MAX_ATTEMPTS = 3;

    private final FindCouponOutPort findCouponOutPort;

    private final SaveCouponOutPort saveCouponOutPort;

    @Override
    public CouponDiscount applyCoupon(final String code, final BigDecimal orderTotal, final Date now) {

        CouponConflictException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            final Coupon coupon = findCouponOutPort.find(normalizeCode(code));
            if (coupon == null) {

                return null;
            }
            if (!coupon.isValidFor(orderTotal, now)) {

                throw new CouponNotApplicableException(
                        coupon.getCode(),
                        "it is inactive, expired, exhausted, or below minimum amount");
            }
            final BigDecimal discountAmount = coupon.calculateDiscount(orderTotal);
            try {

                saveCouponOutPort.save(coupon.incrementRedemptionCount());
                return new CouponDiscount(coupon.getCode(), discountAmount);
            } catch (final CouponConflictException conflict) {

                lastConflict = conflict;
            }
        }
        throw lastConflict;
    }

    private String normalizeCode(final String code) {

        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

}
