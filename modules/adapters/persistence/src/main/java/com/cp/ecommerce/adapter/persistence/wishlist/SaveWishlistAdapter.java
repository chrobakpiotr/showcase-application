package com.cp.ecommerce.adapter.persistence.wishlist;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntity;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntityRepository;
import com.cp.ecommerce.adapter.persistence.wishlist.mapper.WishlistPersistenceMapper;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.port.outgoing.SaveWishlistOutPort;
import com.cp.ecommerce.foundation.exception.WishlistConflictException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveWishlistOutPort}.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class SaveWishlistAdapter implements SaveWishlistOutPort {

    private final WishlistEntityRepository wishlistEntityRepository;

    private final WishlistPersistenceMapper wishlistPersistenceMapper;

    @Override
    public Wishlist save(final Wishlist wishlist) {

        final WishlistEntity entityToSave = wishlistPersistenceMapper.mapToEntity(wishlist)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map wishlist domain object to entity for id: " + wishlist.getWishlistId()));
        try {

            final WishlistEntity saved = wishlistEntityRepository.saveAndFlush(entityToSave);
            return wishlistPersistenceMapper.mapToDomainObject(saved)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Failed to map wishlist entity to domain object for id: " + wishlist.getWishlistId()));
        } catch (final OptimisticLockingFailureException conflict) {

            throw new WishlistConflictException(wishlist.getWishlistId(), conflict);
        }
    }

}
