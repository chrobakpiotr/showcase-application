package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderNotCancellableException}.
 */
class OrderNotCancellableExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final OrderNotCancellableException exception = new OrderNotCancellableException("order is not cancellable");

        assertThat(exception).hasMessage("order is not cancellable").isInstanceOf(BusinessRuleException.class);
    }

}
