package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.adapter.common.exception.CouponAlreadyExistsException;
import com.cp.ecommerce.adapter.common.exception.CouponNotApplicableException;
import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.DiscountType;
import com.cp.ecommerce.domain.coupon.PagedCoupons;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponsOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ManageCouponUseCaseTest {

    @InjectMocks
    private transient ManageCouponUseCase manageCouponUseCase;

    @Mock
    private transient FindCouponOutPort findCouponOutPort;

    @Mock
    private transient FindCouponsOutPort findCouponsOutPort;

    @Mock
    private transient SaveCouponOutPort saveCouponOutPort;

    @Test
    void shouldCreateCouponWithUppercaseCode() {

        final Coupon draft = Coupon.builder()
                .code("save10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();
        given(saveCouponOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        final Coupon created = manageCouponUseCase.createCoupon(draft);
        assertThat(created.getCode()).isEqualTo("SAVE10");
    }

    @Test
    void shouldRejectDuplicateCouponCode() {

        given(findCouponOutPort.find("SAVE10")).willReturn(mockCoupon());
        final Coupon draft = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();
        assertThatThrownBy(() -> manageCouponUseCase.createCoupon(draft)).isInstanceOf(CouponAlreadyExistsException.class);
    }

    @Test
    void shouldReturnCouponByCode() {

        final Coupon coupon = mockCoupon();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        assertThat(manageCouponUseCase.getCoupon("save10")).isEqualTo(coupon);
    }

    @Test
    void shouldListCoupons() {

        final PagedCoupons page = new PagedCoupons(java.util.List.of(mockCoupon()), 0, 10, 1, 1);
        final CouponPageQuery query = new CouponPageQuery(0, 10, true);
        given(findCouponsOutPort.findCoupons(query)).willReturn(page);
        assertThat(manageCouponUseCase.listCoupons(query)).isEqualTo(page);
    }

    @Test
    void shouldPreviewApplicableCoupon() {

        final Coupon coupon = mockCoupon();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        assertThat(manageCouponUseCase.previewCoupon("save10", new BigDecimal("100.00"), new Date()).discountAmount())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectNonApplicablePreview() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .active(false)
                .build();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        assertThatThrownBy(() -> manageCouponUseCase.previewCoupon("SAVE10", new BigDecimal("100.00"), new Date()))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    private Coupon mockCoupon() {

        return Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .minimumOrderAmount(new BigDecimal("50.00"))
                .maxRedemptions(100)
                .redemptionCount(2)
                .expiresAt(new Date(System.currentTimeMillis() + 86400000))
                .active(true)
                .build();
    }

}
