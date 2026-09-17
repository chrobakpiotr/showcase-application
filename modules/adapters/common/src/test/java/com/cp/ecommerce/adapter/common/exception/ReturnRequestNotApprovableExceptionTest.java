package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReturnRequestNotApprovableException}.
 */
class ReturnRequestNotApprovableExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final ReturnRequestNotApprovableException exception = new ReturnRequestNotApprovableException(
                "return request cannot be approved");

        assertThat(exception).hasMessage("return request cannot be approved").isInstanceOf(BusinessRuleException.class);
    }

}
