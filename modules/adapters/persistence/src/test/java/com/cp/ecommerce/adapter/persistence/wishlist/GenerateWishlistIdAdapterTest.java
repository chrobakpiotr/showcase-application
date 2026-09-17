package com.cp.ecommerce.adapter.persistence.wishlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateWishlistIdAdapter}.
 */
class GenerateWishlistIdAdapterTest {

    private final transient GenerateWishlistIdAdapter generateWishlistIdAdapter = new GenerateWishlistIdAdapter();

    @Test
    void shouldGenerateWishlistIdWithExpectedPrefix() {

        final String wishlistId = generateWishlistIdAdapter.generate();

        assertTrue(wishlistId.startsWith("WISHLIST-"));
    }

    @Test
    void shouldGenerateUniqueWishlistIdOnEachCall() {

        assertNotEquals(generateWishlistIdAdapter.generate(), generateWishlistIdAdapter.generate());
    }

}
