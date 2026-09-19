package com.cp.ecommerce.adapter.persistence.coupon;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntityRepository;
import com.cp.ecommerce.adapter.persistence.coupon.mapper.CouponPersistenceMapper;
import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;
import com.cp.ecommerce.foundation.exception.CouponConflictException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveCouponOutPort}.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class SaveCouponAdapter implements SaveCouponOutPort {

    private final CouponEntityRepository couponEntityRepository;

    private final CouponPersistenceMapper couponPersistenceMapper;

    @Override
    public Coupon save(final Coupon coupon) {

        final CouponEntity entityToSave = couponPersistenceMapper.mapToEntity(coupon)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map coupon domain object to entity for code: " + coupon.getCode()));
        try {

            final CouponEntity saved = couponEntityRepository.saveAndFlush(entityToSave);
            return couponPersistenceMapper.mapToDomainObject(saved)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Failed to map coupon entity to domain object for code: " + coupon.getCode()));
        } catch (final OptimisticLockingFailureException conflict) {

            throw new CouponConflictException(coupon.getCode(), conflict);
        }
    }

}
