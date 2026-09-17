package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentDeclinedException}.
 */
class PaymentDeclinedExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final PaymentDeclinedException exception = new PaymentDeclinedException("Payment gateway declined charge");

        assertThat(exception).hasMessage("Payment gateway declined charge").isInstanceOf(BusinessRuleException.class);
    }

}
