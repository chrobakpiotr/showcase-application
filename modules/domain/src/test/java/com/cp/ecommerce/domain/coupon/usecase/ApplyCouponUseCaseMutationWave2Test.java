package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.DiscountType;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;
import com.cp.ecommerce.foundation.exception.CouponConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplyCouponUseCaseMutationWave2Test {

    @Mock
    private FindCouponOutPort findCouponOutPort;
    @Mock
    private SaveCouponOutPort saveCouponOutPort;
    @InjectMocks
    private ApplyCouponUseCase useCase;

    @Test
    void shouldUseThirdAndFinalRetryAttempt() {
        final Coupon coupon = Coupon.builder()
                .code("SAVE10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .build();
        given(findCouponOutPort.find("SAVE10")).willReturn(coupon);
        given(saveCouponOutPort.save(any())).willThrow(new CouponConflictException("SAVE10", new RuntimeException("first")))
                .willThrow(new CouponConflictException("SAVE10", new RuntimeException("second")))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(useCase.applyCoupon("SAVE10", new BigDecimal("100"), Instant.EPOCH)).isNotNull();

        verify(findCouponOutPort, times(3)).find("SAVE10");
        verify(saveCouponOutPort, times(3)).save(any());
    }
}
