package com.cp.ecommerce.adapter.persistence.coupon.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link CouponEntity}.
 */
public interface CouponEntityRepository extends JpaRepository<CouponEntity, String> {

    Page<CouponEntity> findAllByActive(boolean active, Pageable pageable);

}
