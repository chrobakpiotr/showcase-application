package com.cp.ecommerce.domain.wishlist.usecase;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;
import com.cp.ecommerce.domain.wishlist.port.outgoing.FindWishlistOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.GenerateWishlistIdOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.SaveWishlistOutPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ManageWishlistUseCaseMutationWave3Test {

    private static final String WISHLIST_ID = "WISH-1";
    private static final Instant ADDED = Instant.parse("2026-09-20T10:00:00Z");

    @Mock
    private FindWishlistOutPort findWishlistOutPort;
    @Mock
    private SaveWishlistOutPort saveWishlistOutPort;
    @Mock
    private GenerateWishlistIdOutPort generateWishlistIdOutPort;

    private ManageWishlistUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageWishlistUseCase(findWishlistOutPort, saveWishlistOutPort, generateWishlistIdOutPort);
    }

    @Test
    void shouldAddDifferentSkuToNonEmptyWishlist() {
        final Wishlist existing = Wishlist.builder()
                .wishlistId(WISHLIST_ID)
                .items(List.of(item("SKU-1", "First")))
                .version(3)
                .build();

        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = useCase.addItem(WISHLIST_ID, "SKU-2", "Second");

        assertThat(result.getItems()).extracting(WishlistItem::getSku).containsExactly("SKU-1", "SKU-2");
        assertThat(result.getVersion()).isEqualTo(3);
    }

    @Test
    void shouldRemoveOnlyMatchingSkuAndKeepOtherItems() {
        final Wishlist existing = Wishlist.builder()
                .wishlistId(WISHLIST_ID)
                .items(List.of(item("SKU-KEEP", "Keep"), item("SKU-REMOVE", "Remove")))
                .version(3)
                .build();

        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = useCase.removeItem(WISHLIST_ID, "SKU-REMOVE");

        assertThat(result.getItems()).extracting(WishlistItem::getSku).containsExactly("SKU-KEEP");
        assertThat(result.getVersion()).isEqualTo(3);
    }

    private static WishlistItem item(final String sku, final String name) {
        return WishlistItem.builder().sku(sku).productName(name).addedDate(ADDED).build();
    }
}
