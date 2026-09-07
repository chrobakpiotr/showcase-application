package com.cp.ecommerce.adapter.web.coupon.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponDetailsResource;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponResource;
import com.cp.ecommerce.domain.coupon.Coupon;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Coupon} domain object to and from its web resources.
 */
@Component
public class CouponWebMapper
        implements WebRequestMapper<Coupon, CouponResource>, WebResponseMapper<Coupon, CouponDetailsResource> {

    @Override
    public Optional<Coupon> mapToDomainObject(final CouponResource resource) {

        return Optional.ofNullable(resource)
                .map(
                        coupon -> Coupon.builder()
                                .code(coupon.code())
                                .discountType(coupon.discountType())
                                .discountValue(coupon.discountValue())
                                .minimumOrderAmount(coupon.minimumOrderAmount())
                                .maxRedemptions(coupon.maxRedemptions())
                                .expiresAt(coupon.expiresAt())
                                .active(coupon.active() == null || coupon.active())
                                .build());
    }

    @Override
    public Optional<CouponDetailsResource> mapToResource(final Coupon coupon) {

        return Optional.ofNullable(coupon)
                .map(
                        domain -> CouponDetailsResource.builder()
                                .code(domain.getCode())
                                .discountType(domain.getDiscountType())
                                .discountValue(domain.getDiscountValue())
                                .minimumOrderAmount(domain.getMinimumOrderAmount())
                                .maxRedemptions(domain.getMaxRedemptions())
                                .redemptionCount(domain.getRedemptionCount())
                                .expiresAt(domain.getExpiresAt())
                                .active(domain.isActive())
                                .build());
    }

}
