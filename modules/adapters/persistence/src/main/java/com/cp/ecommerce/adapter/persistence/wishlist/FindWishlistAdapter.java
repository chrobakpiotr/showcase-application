package com.cp.ecommerce.adapter.persistence.wishlist;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntityRepository;
import com.cp.ecommerce.adapter.persistence.wishlist.mapper.WishlistPersistenceMapper;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.port.outgoing.FindWishlistOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindWishlistOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindWishlistAdapter implements FindWishlistOutPort {

    private final WishlistEntityRepository wishlistEntityRepository;

    private final WishlistPersistenceMapper wishlistPersistenceMapper;

    @Override
    public Wishlist find(final String wishlistId) {

        return wishlistEntityRepository.findById(wishlistId).flatMap(wishlistPersistenceMapper::mapToDomainObject).orElse(null);
    }

}
