package com.cp.ecommerce.adapter.web.exception;

import com.cp.ecommerce.adapter.common.exception.WishlistConflictException;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of the {@link WishlistExceptionHandler} behavior.
 */
class WishlistExceptionHandlerTest {

    private final transient WishlistExceptionHandler handler = new WishlistExceptionHandler();

    @Test
    void shouldHandleWishlistConflictException() {

        final var problem = handler
                .wishlistConflictException(new WishlistConflictException("WISHLIST-1", new IllegalStateException()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Wishlist Conflict");
        assertThat(problem.getDetail())
                .isEqualTo("Concurrent wishlist modification detected for wishlist WISHLIST-1, please retry");
        assertThat(problem.getType()).hasToString("urn:problem-type:wishlist-conflict");
        assertThat(problem.getProperties()).containsKey("errorId");
    }

}
