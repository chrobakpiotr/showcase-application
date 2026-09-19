package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;
import com.cp.ecommerce.domain.coupon.port.incoming.CreateCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.GetCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.ListCouponsInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.ManageCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.PreviewCouponInPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponsOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;
import com.cp.ecommerce.foundation.exception.CouponAlreadyExistsException;
import com.cp.ecommerce.foundation.exception.CouponNotApplicableException;

import lombok.RequiredArgsConstructor;

/**
 * Use case for coupon administration and read-only validation.
 */
@UseCase
@RequiredArgsConstructor
public class ManageCouponUseCase
        implements CreateCouponInPort, ManageCouponInPort, GetCouponInPort, ListCouponsInPort, PreviewCouponInPort {

    private final FindCouponOutPort findCouponOutPort;

    private final FindCouponsOutPort findCouponsOutPort;

    private final SaveCouponOutPort saveCouponOutPort;

    @Override
    public Coupon createCoupon(final Coupon coupon) {

        final Coupon normalized = normalize(coupon);
        normalized.assertValidationsEmpty();
        if (findCouponOutPort.find(normalized.getCode()) != null) {

            throw new CouponAlreadyExistsException(normalized.getCode());
        }
        return saveCouponOutPort.save(normalized);
    }

    @Override
    public Coupon updateCoupon(final String code, final Coupon coupon) {

        final Coupon existing = findCouponOutPort.find(normalizeCode(code));
        if (existing == null) {

            return null;
        }
        final Coupon updated = Coupon.builder()
                .code(existing.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minimumOrderAmount(coupon.getMinimumOrderAmount())
                .maxRedemptions(coupon.getMaxRedemptions())
                .redemptionCount(existing.getRedemptionCount())
                .expiresAt(coupon.getExpiresAt())
                .active(coupon.isActive())
                .version(existing.getVersion())
                .build();
        updated.assertValidationsEmpty();
        return saveCouponOutPort.save(normalize(updated));
    }

    @Override
    public Coupon activateCoupon(final String code) {

        return switchActivation(code, true);
    }

    @Override
    public Coupon deactivateCoupon(final String code) {

        return switchActivation(code, false);
    }

    @Override
    public Coupon getCoupon(final String code) {

        return findCouponOutPort.find(normalizeCode(code));
    }

    @Override
    public PagedCoupons listCoupons(final CouponPageQuery query) {

        return findCouponsOutPort.findCoupons(query);
    }

    @Override
    public CouponDiscount previewCoupon(final String code, final BigDecimal orderTotal, final Instant now) {

        final Coupon coupon = findCouponOutPort.find(normalizeCode(code));
        if (coupon == null) {

            return null;
        }
        ensureApplicable(coupon, orderTotal, now);
        return new CouponDiscount(coupon.getCode(), coupon.calculateDiscount(orderTotal));
    }

    private Coupon switchActivation(final String code, final boolean active) {

        final Coupon existing = findCouponOutPort.find(normalizeCode(code));
        if (existing == null) {

            return null;
        }
        final Coupon updated = Coupon.builder()
                .code(existing.getCode())
                .discountType(existing.getDiscountType())
                .discountValue(existing.getDiscountValue())
                .minimumOrderAmount(existing.getMinimumOrderAmount())
                .maxRedemptions(existing.getMaxRedemptions())
                .redemptionCount(existing.getRedemptionCount())
                .expiresAt(existing.getExpiresAt())
                .active(active)
                .version(existing.getVersion())
                .build();
        updated.assertValidationsEmpty();
        return saveCouponOutPort.save(updated);
    }

    private Coupon normalize(final Coupon coupon) {

        return Coupon.builder()
                .code(normalizeCode(coupon.getCode()))
                .discountType(coupon.getDiscountType())
                .discountValue(normalizeAmount(coupon.getDiscountValue()))
                .minimumOrderAmount(normalizeAmount(coupon.getMinimumOrderAmount()))
                .maxRedemptions(coupon.getMaxRedemptions())
                .redemptionCount(coupon.getRedemptionCount())
                .expiresAt(coupon.getExpiresAt())
                .active(coupon.isActive())
                .version(coupon.getVersion())
                .build();
    }

    private String normalizeCode(final String code) {

        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeAmount(final BigDecimal value) {

        return value == null ? null : value.stripTrailingZeros();
    }

    private void ensureApplicable(final Coupon coupon, final BigDecimal orderTotal, final Instant now) {

        if (!coupon.isValidFor(orderTotal, now)) {

            throw new CouponNotApplicableException(
                    coupon.getCode(),
                    "it is inactive, expired, exhausted, or below minimum amount");
        }
    }

}
