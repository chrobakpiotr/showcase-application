package com.cp.ecommerce.domain.wishlist;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link WishlistItem}.
 */
class WishlistItemTest {

    @Test
    void shouldPassValidationForValidWishlistItem() {

        final WishlistItem item = TestDomainObjectFactory.validWishlistItem();

        assertDoesNotThrow(item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenSkuIsBlank() {

        final WishlistItem item = WishlistItem.builder()
                .sku(" ")
                .productName("name")
                .addedDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenProductNameIsBlank() {

        final WishlistItem item = WishlistItem.builder()
                .sku("SKU-1")
                .productName(" ")
                .addedDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenAddedDateIsNull() {

        final WishlistItem item = WishlistItem.builder().sku("SKU-1").productName("name").build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

}
