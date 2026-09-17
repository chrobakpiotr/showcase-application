package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReturnRequestNotRejectableException}.
 */
class ReturnRequestNotRejectableExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final ReturnRequestNotRejectableException exception = new ReturnRequestNotRejectableException(
                "return request cannot be rejected");

        assertThat(exception).hasMessage("return request cannot be rejected").isInstanceOf(BusinessRuleException.class);
    }

}
