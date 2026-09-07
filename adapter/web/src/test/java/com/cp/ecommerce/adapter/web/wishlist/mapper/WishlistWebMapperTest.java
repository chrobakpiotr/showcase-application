package com.cp.ecommerce.adapter.web.wishlist.mapper;

import com.cp.ecommerce.adapter.common.utils.WishlistBuilder;
import com.cp.ecommerce.domain.wishlist.Wishlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link WishlistWebMapper}.
 */
class WishlistWebMapperTest {

    private final transient WishlistWebMapper wishlistWebMapper = new WishlistWebMapper();

    @Test
    void shouldMapToResource() {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();

        final var result = wishlistWebMapper.mapToResource(wishlist);

        assertTrue(result.isPresent());
        assertEquals(wishlist.getWishlistId(), result.get().wishlistId());
        assertEquals(wishlist.getItemCount(), result.get().itemCount());
        assertEquals(wishlist.getItems().size(), result.get().items().size());
        assertEquals(wishlist.getItems().getFirst().getSku(), result.get().items().getFirst().sku());
        assertEquals(wishlist.getItems().getFirst().getProductName(), result.get().items().getFirst().productName());
        assertEquals(wishlist.getItems().getFirst().getAddedDate(), result.get().items().getFirst().addedDate());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(wishlistWebMapper.mapToResource(null).isEmpty());
    }

}
