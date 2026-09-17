package com.cp.ecommerce.adapter.common.utils;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Wishlist} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WishlistBuilder {

    public static final String TEST_WISHLIST_ID = "WISHLIST-1234";

    public static final String TEST_WISHLIST_SKU = "SKU-1234";

    public static final String TEST_WISHLIST_PRODUCT_NAME = "Wireless Mouse";

    public static final Date TEST_WISHLIST_ADDED_DATE = new Date(1710000000000L);

    public static final Date TEST_WISHLIST_UPDATED = new Date(1710003600000L);

    public static final long TEST_WISHLIST_VERSION = 3L;

    public static WishlistItem mockWishlistItem() {

        return WishlistItem.builder()
                .sku(TEST_WISHLIST_SKU)
                .productName(TEST_WISHLIST_PRODUCT_NAME)
                .addedDate(TEST_WISHLIST_ADDED_DATE)
                .build();
    }

    public static Wishlist mockWishlist() {

        return Wishlist.builder()
                .wishlistId(TEST_WISHLIST_ID)
                .items(List.of(mockWishlistItem()))
                .updated(TEST_WISHLIST_UPDATED)
                .version(TEST_WISHLIST_VERSION)
                .build();
    }

}
