package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CartConflictException}.
 */
class CartConflictExceptionTest {

    @Test
    void shouldBuildMessageFromCartId() {

        final Throwable cause = new IllegalStateException("stale version");

        final CartConflictException exception = new CartConflictException("CART-1", cause);

        assertThat(exception).hasMessage("Concurrent cart modification detected for cart CART-1, please retry")
                .hasCause(cause)
                .isInstanceOf(BusinessRuleException.class);
    }

}
