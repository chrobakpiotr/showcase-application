package com.cp.ecommerce.adapter.persistence.coupon.mapper;

import com.cp.ecommerce.adapter.common.utils.CouponBuilder;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.adapter.persistence.utils.CouponEntityBuilder;
import com.cp.ecommerce.domain.coupon.Coupon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link CouponPersistenceMapper}.
 */
class CouponPersistenceMapperTest {

    private final transient CouponPersistenceMapper couponPersistenceMapper = new CouponPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final Coupon coupon = CouponBuilder.mockCoupon();

        final var result = couponPersistenceMapper.mapToEntity(coupon);

        assertTrue(result.isPresent());
        assertEquals(coupon.getCode(), result.get().getCode());
        assertEquals(coupon.getDiscountType(), result.get().getDiscountType());
        assertEquals(coupon.getDiscountValue(), result.get().getDiscountValue());
        assertEquals(coupon.getMinimumOrderAmount(), result.get().getMinimumOrderAmount());
        assertEquals(coupon.getMaxRedemptions(), result.get().getMaxRedemptions());
        assertEquals(coupon.getRedemptionCount(), result.get().getRedemptionCount());
        assertEquals(coupon.getExpiresAt(), result.get().getExpiresAt());
        assertEquals(coupon.isActive(), result.get().isActive());
        assertEquals(coupon.getVersion(), result.get().getVersion());
    }

    @Test
    void shouldMapToDomainObject() {

        final CouponEntity entity = CouponEntityBuilder.mockCouponEntity();

        final var result = couponPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getCode(), result.get().getCode());
        assertEquals(entity.getDiscountType(), result.get().getDiscountType());
        assertEquals(entity.getDiscountValue(), result.get().getDiscountValue());
        assertEquals(entity.getMinimumOrderAmount(), result.get().getMinimumOrderAmount());
        assertEquals(entity.getMaxRedemptions(), result.get().getMaxRedemptions());
        assertEquals(entity.getRedemptionCount(), result.get().getRedemptionCount());
        assertEquals(entity.getExpiresAt(), result.get().getExpiresAt());
        assertEquals(entity.isActive(), result.get().isActive());
        assertEquals(entity.getVersion(), result.get().getVersion());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullValues() {

        assertTrue(couponPersistenceMapper.mapToEntity(null).isEmpty());
        assertTrue(couponPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
