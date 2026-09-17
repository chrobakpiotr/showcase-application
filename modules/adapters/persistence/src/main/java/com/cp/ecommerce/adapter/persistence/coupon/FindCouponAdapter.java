package com.cp.ecommerce.adapter.persistence.coupon;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntityRepository;
import com.cp.ecommerce.adapter.persistence.coupon.mapper.CouponPersistenceMapper;
import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindCouponOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCouponAdapter implements FindCouponOutPort {

    private final CouponEntityRepository couponEntityRepository;

    private final CouponPersistenceMapper couponPersistenceMapper;

    @Override
    public Coupon find(final String code) {

        return couponEntityRepository.findById(code).flatMap(couponPersistenceMapper::mapToDomainObject).orElse(null);
    }

}
