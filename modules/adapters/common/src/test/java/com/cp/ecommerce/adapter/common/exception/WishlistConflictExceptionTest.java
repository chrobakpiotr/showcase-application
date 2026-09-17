package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WishlistConflictException}.
 */
class WishlistConflictExceptionTest {

    @Test
    void shouldBuildMessageFromWishlistId() {

        final Throwable cause = new IllegalStateException("stale version");

        final WishlistConflictException exception = new WishlistConflictException("WISHLIST-1", cause);

        assertThat(exception).hasMessage("Concurrent wishlist modification detected for wishlist WISHLIST-1, please retry")
                .hasCause(cause)
                .isInstanceOf(BusinessRuleException.class);
    }

}
