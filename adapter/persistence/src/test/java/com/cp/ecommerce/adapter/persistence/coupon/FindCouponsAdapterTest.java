package com.cp.ecommerce.adapter.persistence.coupon;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CouponBuilder;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntity;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntityRepository;
import com.cp.ecommerce.adapter.persistence.coupon.mapper.CouponPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.CouponEntityBuilder;
import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link FindCouponsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCouponsAdapterTest {

    @InjectMocks
    private transient FindCouponsAdapter findCouponsAdapter;

    @Mock
    private transient CouponEntityRepository couponEntityRepository;

    @Mock
    private transient CouponPersistenceMapper couponPersistenceMapper;

    @Test
    void shouldMapPageOfEntitiesToPagedCoupons() {

        final CouponEntity entity = CouponEntityBuilder.mockCouponEntity();
        final Coupon coupon = CouponBuilder.mockCoupon();
        final Page<CouponEntity> page = new PageImpl<>(List.of(entity), Pageable.ofSize(20), 1);
        given(couponEntityRepository.findAll(any(Pageable.class))).willReturn(page);
        given(couponPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(coupon));

        final PagedCoupons result = findCouponsAdapter.findCoupons(new CouponPageQuery(0, 20, null));

        assertThat(result.content()).containsExactly(coupon);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void shouldRequestPageableSortedByCodeAscending() {

        given(couponEntityRepository.findAll(any(Pageable.class))).willReturn(Page.empty());

        findCouponsAdapter.findCoupons(new CouponPageQuery(2, 10, null));

        final ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(couponEntityRepository).findAll(pageableCaptor.capture());
        final Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "code"));
    }

    @Test
    void shouldFilterByActiveCouponsWhenRequested() {

        final CouponEntity entity = CouponEntityBuilder.mockCouponEntity();
        final Coupon coupon = CouponBuilder.mockCoupon();
        given(couponEntityRepository.findAllByActive(any(Boolean.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(entity), Pageable.ofSize(10), 1));
        given(couponPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(coupon));

        final PagedCoupons result = findCouponsAdapter.findCoupons(new CouponPageQuery(0, 10, true));

        assertThat(result.content()).containsExactly(coupon);
    }

    @Test
    void shouldThrowExceptionWhenMappingFails() {

        final CouponEntity entity = CouponEntityBuilder.mockCouponEntity();
        final Page<CouponEntity> page = new PageImpl<>(List.of(entity));
        given(couponEntityRepository.findAll(any(Pageable.class))).willReturn(page);
        given(couponPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.empty());

        assertThatIllegalStateException().isThrownBy(() -> findCouponsAdapter.findCoupons(new CouponPageQuery(0, 20, null)));
    }

}
