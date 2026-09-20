package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.DiscountType;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.FindCouponsOutPort;
import com.cp.ecommerce.domain.coupon.port.outgoing.SaveCouponOutPort;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageCouponUseCaseMutationWave2Test {

    @Mock
    private FindCouponOutPort findCouponOutPort;
    @Mock
    private FindCouponsOutPort findCouponsOutPort;
    @Mock
    private SaveCouponOutPort saveCouponOutPort;

    private ManageCouponUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageCouponUseCase(findCouponOutPort, findCouponsOutPort, saveCouponOutPort);
    }

    @Test
    void shouldValidateUpdatedCouponBeforeSave() {
        given(findCouponOutPort.find("SAVE10")).willReturn(validCoupon());
        final Coupon invalidUpdate = Coupon.builder().code("IGNORED").discountType(null).discountValue(BigDecimal.TEN).build();

        assertThatThrownBy(() -> useCase.updateCoupon("SAVE10", invalidUpdate))
                .isInstanceOf(DomainObjectValidationException.class);
        verify(saveCouponOutPort, never()).save(any());
    }

    @Test
    void shouldValidateActivationResultBeforeSave() {
        final Coupon invalidExisting = Coupon.builder().code("SAVE10").discountType(null).discountValue(BigDecimal.TEN).build();
        given(findCouponOutPort.find("SAVE10")).willReturn(invalidExisting);

        assertThatThrownBy(() -> useCase.activateCoupon("SAVE10")).isInstanceOf(DomainObjectValidationException.class);
        verify(saveCouponOutPort, never()).save(any());
    }

    private static Coupon validCoupon() {
        return Coupon.builder().code("SAVE10").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).build();
    }
}
