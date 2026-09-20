package com.cp.ecommerce.domain.coupon.usecase;

import java.math.BigDecimal;
import java.time.Instant;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageCouponUseCaseMutationTest {

    private static final String CODE = "SAVE10";

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
    void shouldNormalizeCodeAndAmountsBeforeDuplicateLookupAndSave() {
        given(saveCouponOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Coupon draft = Coupon.builder()
                .code("  save10  ")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10.500"))
                .minimumOrderAmount(new BigDecimal("25.000"))
                .build();

        final Coupon created = useCase.createCoupon(draft);

        verify(findCouponOutPort).find(CODE);
        assertThat(created.getCode()).isEqualTo(CODE);
        assertThat(created.getDiscountValue()).isEqualTo(new BigDecimal("10.5"));
        assertThat(created.getDiscountValue().scale()).isEqualTo(1);
        assertThat(created.getMinimumOrderAmount()).isEqualTo(new BigDecimal("25"));
        assertThat(created.getMinimumOrderAmount().scale()).isZero();
    }

    @Test
    void shouldReturnNullAndAvoidSaveWhenUpdatingMissingCoupon() {
        given(findCouponOutPort.find(CODE)).willReturn(null);

        assertThat(useCase.updateCoupon(" save10 ", validDraft())).isNull();

        verify(saveCouponOutPort, never()).save(any());
    }

    @Test
    void shouldPreserveIdentityUsageAndVersionWhenUpdatingEditableFields() {
        given(saveCouponOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Coupon existing = existingCoupon(true);
        given(findCouponOutPort.find(CODE)).willReturn(existing);

        final Instant newExpiry = Instant.parse("2027-01-01T00:00:00Z");
        final Coupon requested = Coupon.builder()
                .code("IGNORED")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("7.500"))
                .minimumOrderAmount(new BigDecimal("30.000"))
                .maxRedemptions(22)
                .redemptionCount(99)
                .expiresAt(newExpiry)
                .active(false)
                .version(999)
                .build();

        final Coupon updated = useCase.updateCoupon(" save10 ", requested);

        assertThat(updated.getCode()).isEqualTo(CODE);
        assertThat(updated.getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(updated.getDiscountValue()).isEqualByComparingTo("7.5");
        assertThat(updated.getMinimumOrderAmount()).isEqualByComparingTo("30");
        assertThat(updated.getMaxRedemptions()).isEqualTo(22);
        assertThat(updated.getRedemptionCount()).isEqualTo(existing.getRedemptionCount());
        assertThat(updated.getExpiresAt()).isEqualTo(newExpiry);
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getVersion()).isEqualTo(existing.getVersion());
    }

    @Test
    void shouldPersistBothActivationStatesAndPreserveOtherFields() {
        given(saveCouponOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Coupon existing = existingCoupon(false);
        given(findCouponOutPort.find(CODE)).willReturn(existing);

        final Coupon activated = useCase.activateCoupon(" save10 ");
        final Coupon deactivated = useCase.deactivateCoupon(" SAVE10 ");

        assertThat(activated.isActive()).isTrue();
        assertThat(deactivated.isActive()).isFalse();

        for (final Coupon result : new Coupon[] { activated, deactivated }) {
            assertThat(result.getCode()).isEqualTo(existing.getCode());
            assertThat(result.getDiscountType()).isEqualTo(existing.getDiscountType());
            assertThat(result.getDiscountValue()).isEqualByComparingTo(existing.getDiscountValue());
            assertThat(result.getMinimumOrderAmount()).isEqualByComparingTo(existing.getMinimumOrderAmount());
            assertThat(result.getMaxRedemptions()).isEqualTo(existing.getMaxRedemptions());
            assertThat(result.getRedemptionCount()).isEqualTo(existing.getRedemptionCount());
            assertThat(result.getExpiresAt()).isEqualTo(existing.getExpiresAt());
            assertThat(result.getVersion()).isEqualTo(existing.getVersion());
        }
        verify(saveCouponOutPort, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void shouldReturnNullAndAvoidSaveForMissingActivationTarget() {
        given(findCouponOutPort.find(CODE)).willReturn(null);

        assertThat(useCase.activateCoupon(CODE)).isNull();
        assertThat(useCase.deactivateCoupon(CODE)).isNull();

        verify(saveCouponOutPort, never()).save(any());
    }

    @Test
    void shouldReturnNullForMissingPreviewAndNormalizeLookupCode() {
        given(findCouponOutPort.find(CODE)).willReturn(null);

        assertThat(useCase.previewCoupon(" save10 ", new BigDecimal("100"), Instant.EPOCH)).isNull();

        verify(findCouponOutPort).find(CODE);
    }

    @Test
    void shouldRejectInvalidNormalizedCouponBeforeSaving() {
        final Coupon invalid = Coupon.builder()
                .code("x")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();

        assertThatThrownBy(() -> useCase.createCoupon(invalid)).isInstanceOf(DomainObjectValidationException.class);

        verify(saveCouponOutPort, never()).save(any());
    }

    private static Coupon validDraft() {
        return Coupon.builder().code(CODE).discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10")).build();
    }

    private static Coupon existingCoupon(final boolean active) {
        return Coupon.builder()
                .code(CODE)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .minimumOrderAmount(new BigDecimal("20"))
                .maxRedemptions(15)
                .redemptionCount(4)
                .expiresAt(Instant.parse("2027-12-31T00:00:00Z"))
                .active(active)
                .version(6)
                .build();
    }
}
