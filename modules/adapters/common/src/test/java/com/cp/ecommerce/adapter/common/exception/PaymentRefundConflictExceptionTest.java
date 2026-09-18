package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRefundConflictExceptionTest {

    @Test
    void shouldPreserveMessage() {

        assertThat(new PaymentRefundConflictException("refund conflict")).hasMessage("refund conflict");
    }
}
