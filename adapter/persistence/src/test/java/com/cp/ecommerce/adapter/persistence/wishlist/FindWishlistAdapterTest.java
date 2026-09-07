package com.cp.ecommerce.adapter.persistence.wishlist;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.WishlistBuilder;
import com.cp.ecommerce.adapter.persistence.utils.WishlistEntityBuilder;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntityRepository;
import com.cp.ecommerce.adapter.persistence.wishlist.mapper.WishlistPersistenceMapper;
import com.cp.ecommerce.domain.wishlist.Wishlist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_ID;

/**
 * Test class for {@link FindWishlistAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindWishlistAdapterTest {

    @InjectMocks
    private transient FindWishlistAdapter findWishlistAdapter;

    @Mock
    private transient WishlistEntityRepository wishlistEntityRepository;

    @Mock
    private transient WishlistPersistenceMapper wishlistPersistenceMapper;

    @Test
    void shouldFindWishlistById() {

        final var entity = WishlistEntityBuilder.mockWishlistEntity();
        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        doReturn(Optional.of(entity)).when(wishlistEntityRepository).findById(TEST_WISHLIST_ID);
        doReturn(Optional.of(wishlist)).when(wishlistPersistenceMapper).mapToDomainObject(entity);

        final Wishlist result = findWishlistAdapter.find(TEST_WISHLIST_ID);

        assertEquals(wishlist, result);
    }

    @Test
    void shouldReturnNullWhenWishlistNotFound() {

        doReturn(Optional.empty()).when(wishlistEntityRepository).findById(TEST_WISHLIST_ID);

        final Wishlist result = findWishlistAdapter.find(TEST_WISHLIST_ID);

        assertNull(result);
    }

}
