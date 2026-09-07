package com.cp.ecommerce.adapter.persistence.coupon.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.domain.coupon.Coupon;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Coupon} object into/from entity object.
 */
@Component
public class CouponPersistenceMapper implements PersistenceMapper<Coupon, CouponEntity> {

    @Override
    public Optional<CouponEntity> mapToEntity(final Coupon coupon) {

        return ofNullable(coupon).map(
                domain -> CouponEntity.builder()
                        .code(domain.getCode())
                        .discountType(domain.getDiscountType())
                        .discountValue(domain.getDiscountValue())
                        .minimumOrderAmount(domain.getMinimumOrderAmount())
                        .maxRedemptions(domain.getMaxRedemptions())
                        .redemptionCount(domain.getRedemptionCount())
                        .expiresAt(domain.getExpiresAt())
                        .active(domain.isActive())
                        .version(domain.getVersion())
                        .build());
    }

    @Override
    public Optional<Coupon> mapToDomainObject(final CouponEntity entity) {

        return ofNullable(entity).map(
                couponEntity -> Coupon.builder()
                        .code(couponEntity.getCode())
                        .discountType(couponEntity.getDiscountType())
                        .discountValue(couponEntity.getDiscountValue())
                        .minimumOrderAmount(couponEntity.getMinimumOrderAmount())
                        .maxRedemptions(couponEntity.getMaxRedemptions())
                        .redemptionCount(couponEntity.getRedemptionCount())
                        .expiresAt(couponEntity.getExpiresAt())
                        .active(couponEntity.isActive())
                        .version(couponEntity.getVersion())
                        .build());
    }

}
