package com.cp.ecommerce.adapter.web.wishlist.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.wishlist.resource.WishlistItemResource;
import com.cp.ecommerce.adapter.web.wishlist.resource.WishlistResource;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Wishlist} domain object to its web resource.
 */
@Component
public class WishlistWebMapper implements WebResponseMapper<Wishlist, WishlistResource> {

    @Override
    public Optional<WishlistResource> mapToResource(final Wishlist wishlist) {

        return Optional.ofNullable(wishlist)
                .map(
                        domain -> WishlistResource.builder()
                                .wishlistId(domain.getWishlistId())
                                .items(domain.getItems().stream().map(this::mapItemToResource).toList())
                                .itemCount(domain.getItemCount())
                                .build());
    }

    private WishlistItemResource mapItemToResource(final WishlistItem item) {

        return WishlistItemResource.builder()
                .sku(item.getSku())
                .productName(item.getProductName())
                .addedDate(item.getAddedDate())
                .build();
    }

}
