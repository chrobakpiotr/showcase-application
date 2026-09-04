package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InsufficientStockException}.
 */
class InsufficientStockExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final InsufficientStockException exception = new InsufficientStockException("not enough stock");

        assertThat(exception).hasMessage("not enough stock").isInstanceOf(BusinessRuleException.class);
    }

}
