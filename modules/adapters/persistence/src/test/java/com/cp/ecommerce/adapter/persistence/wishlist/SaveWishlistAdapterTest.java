package com.cp.ecommerce.adapter.persistence.wishlist;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.WishlistBuilder;
import com.cp.ecommerce.adapter.persistence.utils.WishlistEntityBuilder;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntity;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntityRepository;
import com.cp.ecommerce.adapter.persistence.wishlist.mapper.WishlistPersistenceMapper;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.foundation.exception.WishlistConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Test class for {@link SaveWishlistAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveWishlistAdapterTest {

    @InjectMocks
    private transient SaveWishlistAdapter saveWishlistAdapter;

    @Mock
    private transient WishlistEntityRepository wishlistEntityRepository;

    @Mock
    private transient WishlistPersistenceMapper wishlistPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedWishlist() {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        final WishlistEntity mappedEntity = WishlistEntityBuilder.mockWishlistEntity();
        doReturn(Optional.of(mappedEntity)).when(wishlistPersistenceMapper).mapToEntity(eq(wishlist));
        doReturn(mappedEntity).when(wishlistEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.of(wishlist)).when(wishlistPersistenceMapper).mapToDomainObject(mappedEntity);

        final Wishlist result = saveWishlistAdapter.save(wishlist);

        assertEquals(wishlist, result);
    }

    @Test
    void shouldThrowWishlistConflictExceptionWhenOptimisticLockFails() {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        final WishlistEntity mappedEntity = WishlistEntityBuilder.mockWishlistEntity();
        doReturn(Optional.of(mappedEntity)).when(wishlistPersistenceMapper).mapToEntity(eq(wishlist));
        doThrow(new OptimisticLockingFailureException("conflict")).when(wishlistEntityRepository).saveAndFlush(mappedEntity);

        assertThrows(WishlistConflictException.class, () -> saveWishlistAdapter.save(wishlist));
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        doReturn(Optional.empty()).when(wishlistPersistenceMapper).mapToEntity(eq(wishlist));

        assertThrows(IllegalStateException.class, () -> saveWishlistAdapter.save(wishlist));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Wishlist wishlist = WishlistBuilder.mockWishlist();
        final WishlistEntity mappedEntity = WishlistEntityBuilder.mockWishlistEntity();
        doReturn(Optional.of(mappedEntity)).when(wishlistPersistenceMapper).mapToEntity(eq(wishlist));
        doReturn(mappedEntity).when(wishlistEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.empty()).when(wishlistPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveWishlistAdapter.save(wishlist));
    }

}
