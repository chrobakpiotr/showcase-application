package com.cp.ecommerce.adapter.persistence.utils;

import java.util.List;

import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistEntity;
import com.cp.ecommerce.adapter.persistence.wishlist.entity.WishlistItemEmbeddable;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_ADDED_DATE;
import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_ID;
import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_PRODUCT_NAME;
import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_SKU;
import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_UPDATED;
import static com.cp.ecommerce.adapter.common.utils.WishlistBuilder.TEST_WISHLIST_VERSION;

/**
 * Builder class for {@link WishlistEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WishlistEntityBuilder {

    public static WishlistItemEmbeddable mockWishlistItemEmbeddable() {

        return WishlistItemEmbeddable.builder()
                .sku(TEST_WISHLIST_SKU)
                .productName(TEST_WISHLIST_PRODUCT_NAME)
                .addedDate(TEST_WISHLIST_ADDED_DATE)
                .build();
    }

    public static WishlistEntity mockWishlistEntity() {

        return WishlistEntity.builder()
                .wishlistId(TEST_WISHLIST_ID)
                .items(List.of(mockWishlistItemEmbeddable()))
                .updated(TEST_WISHLIST_UPDATED)
                .version(TEST_WISHLIST_VERSION)
                .build();
    }

}
