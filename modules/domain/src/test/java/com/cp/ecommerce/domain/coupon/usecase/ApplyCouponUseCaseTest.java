package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponDiscount;
import com.cp.ecommerce.domain.coupon.DiscountType;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;
import com.cp.ecommerce.foundation.exception.CouponConflictException;
import com.cp.ecommerce.foundation.exception.CouponNotApplicableException;

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
class ApplyCouponUseCaseTest {

    @InjectMocks
    private transient ApplyCouponUseCase applyCouponUseCase;

    @Mock
    private transient FindCouponOutPort findCouponOutPort;

    @Mock
    private transient SaveCouponOutPort saveCouponOutPort;

    @Test
    void shouldApplyCouponAndIncrementRedemptionCount() {

        final Coupon coupon = mockCoupon();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        given(saveCouponOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        final CouponDiscount result = applyCouponUseCase
                .applyCoupon("save10", new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        assertThat(result.code()).isEqualTo("SAVE10");
        assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldReturnNullWhenCouponNotFound() {

        assertThat(
                applyCouponUseCase
                        .applyCoupon("save10", new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli())))
                .isNull();
    }

    @Test
    void shouldRetryOnConflict() {

        final Coupon coupon = mockCoupon();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        given(saveCouponOutPort.save(any())).willThrow(new CouponConflictException("SAVE10", new RuntimeException()))
                .willAnswer(invocation -> invocation.getArgument(0));
        assertThat(
                applyCouponUseCase
                        .applyCoupon("SAVE10", new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                        .discountAmount())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void shouldThrowLastConflictWhenRetryBudgetExhausted() {

        final Coupon coupon = mockCoupon();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        given(saveCouponOutPort.save(any())).willThrow(new CouponConflictException("SAVE10", new RuntimeException()));
        assertThatThrownBy(
                () -> applyCouponUseCase
                        .applyCoupon("SAVE10", new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli())))
                .isInstanceOf(CouponConflictException.class);
    }

    @Test
    void shouldRejectNonApplicableCoupon() {

        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .active(false)
                .build();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        assertThatThrownBy(
                () -> applyCouponUseCase
                        .applyCoupon("SAVE10", new BigDecimal("100.00"), Instant.ofEpochMilli(Instant.now().toEpochMilli())))
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
                .expiresAt(Instant.ofEpochMilli(System.currentTimeMillis() + 86400000))
                .active(true)
                .build();
    }

}
