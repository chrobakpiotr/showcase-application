package com.cp.ecommerce.adapter.persistence.coupon;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntityRepository;
import com.cp.ecommerce.adapter.persistence.coupon.mapper.CouponPersistenceMapper;
import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponsOutPort;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindCouponsOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCouponsAdapter implements FindCouponsOutPort {

    private final CouponEntityRepository couponEntityRepository;

    private final CouponPersistenceMapper couponPersistenceMapper;

    @Override
    public PagedCoupons findCoupons(final CouponPageQuery query) {

        final PageRequest pageable = PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.ASC, "code"));
        final Page<CouponEntity> page = Boolean.TRUE.equals(query.activeOnly())
                ? couponEntityRepository.findAllByActive(true, pageable)
                : couponEntityRepository.findAll(pageable);
        return new PagedCoupons(
                page.map(
                        entity -> couponPersistenceMapper.mapToDomainObject(entity)
                                .orElseThrow(() -> new IllegalStateException("Failed to map coupon entity to domain object")))
                        .getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

}
