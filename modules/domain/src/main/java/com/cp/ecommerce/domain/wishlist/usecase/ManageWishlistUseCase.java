package com.cp.ecommerce.domain.wishlist.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;
import com.cp.ecommerce.domain.wishlist.port.incoming.CreateWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.incoming.GetWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.incoming.ManageWishlistInPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.FindWishlistOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.GenerateWishlistIdOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.SaveWishlistOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating wishlists and mutating their remembered items.
 */
@UseCase
@RequiredArgsConstructor
public class ManageWishlistUseCase implements CreateWishlistInPort, GetWishlistInPort, ManageWishlistInPort {

    private final FindWishlistOutPort findWishlistOutPort;

    private final SaveWishlistOutPort saveWishlistOutPort;

    private final GenerateWishlistIdOutPort generateWishlistIdOutPort;

    @Override
    public Wishlist createWishlist() {

        final Wishlist wishlist = Wishlist.builder()
                .wishlistId(generateWishlistIdOutPort.generate())
                .updated(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        wishlist.assertValidationsEmpty();
        return saveWishlistOutPort.save(wishlist);
    }

    @Override
    public Wishlist getWishlist(final String wishlistId) {

        return findWishlistOutPort.find(wishlistId);
    }

    @Override
    public Wishlist addItem(final String wishlistId, final String sku, final String productName) {

        final Wishlist existing = findWishlistOutPort.find(wishlistId);
        if (existing == null) {

            return null;
        }
        final List<WishlistItem> items = new ArrayList<>(existing.getItems());
        final boolean alreadyPresent = items.stream().anyMatch(item -> item.getSku().equals(sku));
        if (alreadyPresent) {

            return existing;
        }
        items.add(
                WishlistItem.builder()
                        .sku(sku)
                        .productName(productName)
                        .addedDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                        .build());
        return persist(existing, items);
    }

    @Override
    public Wishlist removeItem(final String wishlistId, final String sku) {

        final Wishlist existing = findWishlistOutPort.find(wishlistId);
        if (existing == null) {

            return null;
        }
        final List<WishlistItem> items = new ArrayList<>(existing.getItems());
        final boolean changed = items.removeIf(item -> item.getSku().equals(sku));
        if (!changed) {

            return existing;
        }
        return persist(existing, items);
    }

    private Wishlist persist(final Wishlist existing, final List<WishlistItem> items) {

        final Wishlist mutated = Wishlist.builder()
                .wishlistId(existing.getWishlistId())
                .items(items)
                .updated(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .version(existing.getVersion())
                .build();
        mutated.assertValidationsEmpty();
        return saveWishlistOutPort.save(mutated);
    }

}
