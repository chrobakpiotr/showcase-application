package com.cp.ecommerce.adapter.persistence.wishlist.mapper;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntity;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistItemEmbeddable;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Wishlist} object into/from entity object.
 */
@Component
public class WishlistPersistenceMapper implements PersistenceMapper<Wishlist, WishlistEntity> {

    @Override
    public Optional<WishlistEntity> mapToEntity(final Wishlist wishlist) {

        return ofNullable(wishlist).map(
                domain -> WishlistEntity.builder()
                        .wishlistId(domain.getWishlistId())
                        .items(domain.getItems().stream().map(this::mapItemToEmbeddable).toList())
                        .updated(domain.getUpdated())
                        .version(domain.getVersion())
                        .build());
    }

    @Override
    public Optional<Wishlist> mapToDomainObject(final WishlistEntity entity) {

        return ofNullable(entity).map(
                wishlistEntity -> Wishlist.builder()
                        .wishlistId(wishlistEntity.getWishlistId())
                        .items(mapItemsToDomainObjects(wishlistEntity.getItems()))
                        .updated(wishlistEntity.getUpdated())
                        .version(wishlistEntity.getVersion())
                        .build());
    }

    private WishlistItemEmbeddable mapItemToEmbeddable(final WishlistItem item) {

        return WishlistItemEmbeddable.builder()
                .sku(item.getSku())
                .productName(item.getProductName())
                .addedDate(item.getAddedDate())
                .build();
    }

    private List<WishlistItem> mapItemsToDomainObjects(final List<WishlistItemEmbeddable> items) {

        return items.stream()
                .map(
                        item -> WishlistItem.builder()
                                .sku(item.getSku())
                                .productName(item.getProductName())
                                .addedDate(item.getAddedDate())
                                .build())
                .toList();
    }

}
