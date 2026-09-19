package com.cp.ecommerce.domain.wishlist;

import java.util.List;

import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Wishlist}.
 */
class WishlistTest {

    @Test
    void shouldPassValidationForValidWishlist() {

        final Wishlist wishlist = TestDomainObjectFactory.validWishlist();

        assertDoesNotThrow(wishlist::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultToEmptyItemsList() {

        final Wishlist wishlist = Wishlist.builder().wishlistId("WISHLIST-1").build();

        assertThat(wishlist.getItems()).isEmpty();
    }

    @Test
    void shouldDefaultVersionToZero() {

        final Wishlist wishlist = Wishlist.builder().wishlistId("WISHLIST-1").build();

        assertThat(wishlist.getVersion()).isZero();
    }

    @Test
    void shouldComputeItemCountAsNumberOfRememberedSkus() {

        final WishlistItem itemA = WishlistItem.builder()
                .sku("SKU-1")
                .productName("A")
                .addedDate(TestDomainObjectFactory.TEST_CREATED)
                .build();
        final WishlistItem itemB = WishlistItem.builder()
                .sku("SKU-2")
                .productName("B")
                .addedDate(TestDomainObjectFactory.TEST_CREATED)
                .build();
        final Wishlist wishlist = Wishlist.builder().wishlistId("WISHLIST-1").items(List.of(itemA, itemB)).build();

        assertThat(wishlist.getItemCount()).isEqualTo(2);
    }

    @Test
    void shouldFailValidationWhenWishlistIdIsBlank() {

        final Wishlist wishlist = Wishlist.builder().wishlistId(" ").build();

        assertThrows(DomainObjectValidationException.class, wishlist::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenNestedWishlistItemIsInvalid() {

        final WishlistItem invalidItem = WishlistItem.builder()
                .sku(" ")
                .productName("name")
                .addedDate(TestDomainObjectFactory.TEST_CREATED)
                .build();
        final Wishlist wishlist = Wishlist.builder().wishlistId("WISHLIST-1").items(List.of(invalidItem)).build();

        assertThrows(DomainObjectValidationException.class, wishlist::assertValidationsEmpty);
    }

}
