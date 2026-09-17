package com.cp.ecommerce.adapter.web.coupon.mapper;

import com.cp.ecommerce.adapter.common.utils.CouponBuilder;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponDetailsResource;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponResource;
import com.cp.ecommerce.domain.coupon.Coupon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link CouponWebMapper}.
 */
class CouponWebMapperTest {

    private final transient CouponWebMapper couponWebMapper = new CouponWebMapper();

    @Test
    void shouldMapToDomainObject() {

        final Coupon source = CouponBuilder.mockCoupon();
        final CouponResource resource = CouponResource.builder()
                .code(source.getCode())
                .discountType(source.getDiscountType())
                .discountValue(source.getDiscountValue())
                .minimumOrderAmount(source.getMinimumOrderAmount())
                .maxRedemptions(source.getMaxRedemptions())
                .expiresAt(source.getExpiresAt())
                .active(true)
                .build();

        final var result = couponWebMapper.mapToDomainObject(resource);

        assertTrue(result.isPresent());
        assertEquals(resource.code(), result.get().getCode());
        assertEquals(resource.discountType(), result.get().getDiscountType());
        assertEquals(resource.discountValue(), result.get().getDiscountValue());
        assertEquals(resource.minimumOrderAmount(), result.get().getMinimumOrderAmount());
        assertEquals(resource.maxRedemptions(), result.get().getMaxRedemptions());
        assertEquals(resource.expiresAt(), result.get().getExpiresAt());
        assertTrue(result.get().isActive());
    }

    @Test
    void shouldDefaultActiveFlagToTrueWhenMissing() {

        final CouponResource resource = CouponResource.builder()
                .code("SAVE10")
                .discountType(CouponBuilder.mockCoupon().getDiscountType())
                .discountValue(CouponBuilder.mockCoupon().getDiscountValue())
                .build();

        final Coupon result = couponWebMapper.mapToDomainObject(resource).orElseThrow();

        assertTrue(result.isActive());
    }

    @Test
    void shouldPreserveFalseActiveFlag() {

        final CouponResource resource = CouponResource.builder()
                .code("SAVE10")
                .discountType(CouponBuilder.mockCoupon().getDiscountType())
                .discountValue(CouponBuilder.mockCoupon().getDiscountValue())
                .active(false)
                .build();

        final Coupon result = couponWebMapper.mapToDomainObject(resource).orElseThrow();

        assertTrue(!result.isActive());
    }

    @Test
    void shouldMapToResource() {

        final Coupon coupon = CouponBuilder.mockCoupon();

        final var result = couponWebMapper.mapToResource(coupon);

        assertTrue(result.isPresent());
        final CouponDetailsResource resource = result.get();
        assertEquals(coupon.getCode(), resource.code());
        assertEquals(coupon.getDiscountType(), resource.discountType());
        assertEquals(coupon.getDiscountValue(), resource.discountValue());
        assertEquals(coupon.getMinimumOrderAmount(), resource.minimumOrderAmount());
        assertEquals(coupon.getMaxRedemptions(), resource.maxRedemptions());
        assertEquals(coupon.getRedemptionCount(), resource.redemptionCount());
        assertEquals(coupon.getExpiresAt(), resource.expiresAt());
        assertEquals(coupon.isActive(), resource.active());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullValues() {

        assertTrue(couponWebMapper.mapToDomainObject(null).isEmpty());
        assertTrue(couponWebMapper.mapToResource(null).isEmpty());
    }

}
