package com.cp.ecommerce.domain.payment;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link PaymentTransaction}.
 */
class PaymentTransactionTest {

    @Test
    void shouldPassValidationForValidPaymentTransaction() {

        final PaymentTransaction paymentTransaction = TestDomainObjectFactory.validPaymentTransaction();

        assertDoesNotThrow(paymentTransaction::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultToPendingStatusAndZeroAmount() {

        final PaymentTransaction paymentTransaction = PaymentTransaction.builder().orderNumber("ORDER-1").build();

        assertDoesNotThrow(paymentTransaction::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenOrderNumberIsBlank() {

        final PaymentTransaction paymentTransaction = PaymentTransaction.builder().orderNumber(" ").build();

        assertThrows(DomainObjectValidationException.class, paymentTransaction::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenAmountIsNegative() {

        final PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .orderNumber("ORDER-1")
                .amount(new BigDecimal("-1.00"))
                .method(PaymentMethod.CARD)
                .build();

        assertThrows(DomainObjectValidationException.class, paymentTransaction::assertValidationsEmpty);
    }

}
