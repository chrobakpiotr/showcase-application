package com.cp.ecommerce.adapter.persistence.coupon;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CouponBuilder;
import com.cp.ecommerce.adapter.persistence.coupon.entity.CouponEntityRepository;
import com.cp.ecommerce.adapter.persistence.coupon.mapper.CouponPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.CouponEntityBuilder;
import com.cp.ecommerce.domain.coupon.Coupon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.CouponBuilder.TEST_COUPON_CODE;

/**
 * Test class for {@link FindCouponAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCouponAdapterTest {

    @InjectMocks
    private transient FindCouponAdapter findCouponAdapter;

    @Mock
    private transient CouponEntityRepository couponEntityRepository;

    @Mock
    private transient CouponPersistenceMapper couponPersistenceMapper;

    @Test
    void shouldFindCouponByCode() {

        final var entity = CouponEntityBuilder.mockCouponEntity();
        final Coupon coupon = CouponBuilder.mockCoupon();
        doReturn(Optional.of(entity)).when(couponEntityRepository).findById(TEST_COUPON_CODE);
        doReturn(Optional.of(coupon)).when(couponPersistenceMapper).mapToDomainObject(entity);

        final Coupon result = findCouponAdapter.find(TEST_COUPON_CODE);

        assertEquals(coupon, result);
    }

    @Test
    void shouldReturnNullWhenCouponNotFound() {

        doReturn(Optional.empty()).when(couponEntityRepository).findById(TEST_COUPON_CODE);

        final Coupon result = findCouponAdapter.find(TEST_COUPON_CODE);

        assertNull(result);
    }

}
