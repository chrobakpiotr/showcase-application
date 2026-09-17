package com.cp.ecommerce.adapter.persistence.coupon;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.CouponConflictException;
import com.cp.ecommerce.adapter.common.utils.CouponBuilder;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntityRepository;
import com.cp.ecommerce.adapter.persistence.coupon.mapper.CouponPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.CouponEntityBuilder;
import com.cp.ecommerce.domain.coupon.Coupon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Test class for {@link SaveCouponAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveCouponAdapterTest {

    @InjectMocks
    private transient SaveCouponAdapter saveCouponAdapter;

    @Mock
    private transient CouponEntityRepository couponEntityRepository;

    @Mock
    private transient CouponPersistenceMapper couponPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedCoupon() {

        final Coupon coupon = CouponBuilder.mockCoupon();
        final CouponEntity mappedEntity = CouponEntityBuilder.mockCouponEntity();
        doReturn(Optional.of(mappedEntity)).when(couponPersistenceMapper).mapToEntity(eq(coupon));
        doReturn(mappedEntity).when(couponEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.of(coupon)).when(couponPersistenceMapper).mapToDomainObject(mappedEntity);

        final Coupon result = saveCouponAdapter.save(coupon);

        assertEquals(coupon, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Coupon coupon = CouponBuilder.mockCoupon();
        doReturn(Optional.empty()).when(couponPersistenceMapper).mapToEntity(eq(coupon));

        assertThrows(IllegalStateException.class, () -> saveCouponAdapter.save(coupon));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Coupon coupon = CouponBuilder.mockCoupon();
        final CouponEntity mappedEntity = CouponEntityBuilder.mockCouponEntity();
        doReturn(Optional.of(mappedEntity)).when(couponPersistenceMapper).mapToEntity(eq(coupon));
        doReturn(mappedEntity).when(couponEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.empty()).when(couponPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveCouponAdapter.save(coupon));
    }

    @Test
    void shouldWrapOptimisticLockingConflict() {

        final Coupon coupon = CouponBuilder.mockCoupon();
        final CouponEntity mappedEntity = CouponEntityBuilder.mockCouponEntity();
        doReturn(Optional.of(mappedEntity)).when(couponPersistenceMapper).mapToEntity(eq(coupon));
        doThrow(new OptimisticLockingFailureException("conflict")).when(couponEntityRepository).saveAndFlush(mappedEntity);

        assertThrows(CouponConflictException.class, () -> saveCouponAdapter.save(coupon));
    }

}
