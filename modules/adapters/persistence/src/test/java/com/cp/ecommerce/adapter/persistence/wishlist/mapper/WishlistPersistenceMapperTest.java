package com.cp.ecommerce.adapter.persistence.wishlist.mapper;

import com.cp.ecommerce.adapter.common.utils.WishlistBuilder;
import com.cp.ecommerce.adapter.persistence.utils.WishlistEntityBuilder;
import com.cp.ecommerce.domain.wishlist.Wishlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link WishlistPersistenceMapper}.
 */
class WishlistPersistenceMapperTest {

    private final transient WishlistPersistenceMapper wishlistPersistenceMapper = new WishlistPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();

        final var result = wishlistPersistenceMapper.mapToEntity(wishlist);

        assertTrue(result.isPresent());
        assertEquals(wishlist.getWishlistId(), result.get().getWishlistId());
        assertEquals(wishlist.getUpdated(), result.get().getUpdated());
        assertEquals(wishlist.getVersion(), result.get().getVersion());
        assertEquals(wishlist.getItems().size(), result.get().getItems().size());
        assertEquals(wishlist.getItems().getFirst().getSku(), result.get().getItems().getFirst().getSku());
        assertEquals(wishlist.getItems().getFirst().getProductName(), result.get().getItems().getFirst().getProductName());
        assertEquals(wishlist.getItems().getFirst().getAddedDate(), result.get().getItems().getFirst().getAddedDate());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = WishlistEntityBuilder.mockWishlistEntity();

        final var result = wishlistPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getWishlistId(), result.get().getWishlistId());
        assertEquals(entity.getUpdated(), result.get().getUpdated());
        assertEquals(entity.getVersion(), result.get().getVersion());
        assertEquals(entity.getItems().size(), result.get().getItems().size());
        assertEquals(entity.getItems().getFirst().getSku(), result.get().getItems().getFirst().getSku());
        assertEquals(entity.getItems().getFirst().getProductName(), result.get().getItems().getFirst().getProductName());
        assertEquals(entity.getItems().getFirst().getAddedDate(), result.get().getItems().getFirst().getAddedDate());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(wishlistPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(wishlistPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
