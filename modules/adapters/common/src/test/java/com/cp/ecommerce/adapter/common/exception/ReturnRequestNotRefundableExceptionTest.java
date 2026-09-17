package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReturnRequestNotRefundableException}.
 */
class ReturnRequestNotRefundableExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final ReturnRequestNotRefundableException exception = new ReturnRequestNotRefundableException(
                "return request cannot be refunded");

        assertThat(exception).hasMessage("return request cannot be refunded").isInstanceOf(BusinessRuleException.class);
    }

}
