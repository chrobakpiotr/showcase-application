package com.cp.ecommerce.domain.returns;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ReturnRequest}.
 */
class ReturnRequestTest {

    @Test
    void shouldPassValidationForValidReturnRequest() {

        final ReturnRequest returnRequest = TestDomainObjectFactory.validReturnRequest();

        assertDoesNotThrow(returnRequest::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultStatusToRequested() {

        final ReturnRequest returnRequest = ReturnRequest.builder()
                .orderNumber("ORD-1")
                .sku("SKU-1")
                .quantity(1)
                .reason("Damaged")
                .requestedDate(TestDomainObjectFactory.TEST_CREATED)
                .refundAmount(BigDecimal.ONE)
                .build();

        assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    void shouldFailValidationWhenQuantityIsBelowMinimum() {

        final ReturnRequest returnRequest = ReturnRequest.builder()
                .orderNumber("ORD-1")
                .sku("SKU-1")
                .quantity(0)
                .reason("Damaged")
                .requestedDate(TestDomainObjectFactory.TEST_CREATED)
                .refundAmount(BigDecimal.ONE)
                .build();

        assertThrows(DomainObjectValidationException.class, returnRequest::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenReasonIsTooLong() {

        final ReturnRequest returnRequest = ReturnRequest.builder()
                .orderNumber("ORD-1")
                .sku("SKU-1")
                .quantity(1)
                .reason("x".repeat(ValidationConstants.RETURN_REASON_MAX + 1))
                .requestedDate(TestDomainObjectFactory.TEST_CREATED)
                .refundAmount(BigDecimal.ONE)
                .build();

        assertThrows(DomainObjectValidationException.class, returnRequest::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenRefundAmountIsNegative() {

        final ReturnRequest returnRequest = ReturnRequest.builder()
                .orderNumber("ORD-1")
                .sku("SKU-1")
                .quantity(1)
                .reason("Damaged")
                .requestedDate(TestDomainObjectFactory.TEST_CREATED)
                .refundAmount(new BigDecimal("-0.01"))
                .build();

        assertThrows(DomainObjectValidationException.class, returnRequest::assertValidationsEmpty);
    }

}
