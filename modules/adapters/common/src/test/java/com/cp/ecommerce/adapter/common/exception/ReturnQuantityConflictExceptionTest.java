package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReturnQuantityConflictException}.
 */
class ReturnQuantityConflictExceptionTest {

    @Test
    void shouldExposeRemainingQuantity() {

        final ReturnQuantityConflictException exception = new ReturnQuantityConflictException(2);

        assertThat(exception).hasMessage("Requested quantity exceeds the remaining returnable quantity of 2")
                .isInstanceOf(BusinessRuleException.class);
    }

}
