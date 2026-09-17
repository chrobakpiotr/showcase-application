package com.cp.ecommerce.domain.wishlist.usecase;

import java.util.List;

import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;
import com.cp.ecommerce.domain.wishlist.port.outgoing.FindWishlistOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.GenerateWishlistIdOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.SaveWishlistOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ManageWishlistUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageWishlistUseCaseTest {

    private static final String WISHLIST_ID = "WISHLIST-1";

    private static final String SKU = "SKU-1";

    @InjectMocks
    private transient ManageWishlistUseCase manageWishlistUseCase;

    @Mock
    private transient FindWishlistOutPort findWishlistOutPort;

    @Mock
    private transient SaveWishlistOutPort saveWishlistOutPort;

    @Mock
    private transient GenerateWishlistIdOutPort generateWishlistIdOutPort;

    @Test
    void shouldCreateEmptyWishlistWithGeneratedId() {

        given(generateWishlistIdOutPort.generate()).willReturn(WISHLIST_ID);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = manageWishlistUseCase.createWishlist();

        assertThat(result.getWishlistId()).isEqualTo(WISHLIST_ID);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldReturnWishlistFromFindWishlistOutPort() {

        final Wishlist wishlist = Wishlist.builder().wishlistId(WISHLIST_ID).build();
        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(wishlist);

        assertThat(manageWishlistUseCase.getWishlist(WISHLIST_ID)).isEqualTo(wishlist);
    }

    @Test
    void shouldReturnNullFromGetWishlistWhenNotFound() {

        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(null);

        assertThat(manageWishlistUseCase.getWishlist(WISHLIST_ID)).isNull();
    }

    @Test
    void shouldAddNewItemToWishlist() {

        final Wishlist existing = Wishlist.builder().wishlistId(WISHLIST_ID).build();
        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = manageWishlistUseCase.addItem(WISHLIST_ID, SKU, "name");

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getSku()).isEqualTo(SKU);
        assertThat(result.getItems().getFirst().getProductName()).isEqualTo("name");
    }

    @Test
    void shouldNoOpWhenAddingExistingSku() {

        final WishlistItem existingItem = WishlistItem.builder()
                .sku(SKU)
                .productName("old")
                .addedDate(new java.util.Date())
                .build();
        final Wishlist existing = Wishlist.builder().wishlistId(WISHLIST_ID).items(List.of(existingItem)).build();
        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);

        final Wishlist result = manageWishlistUseCase.addItem(WISHLIST_ID, SKU, "new name");

        assertThat(result).isEqualTo(existing);
        verify(saveWishlistOutPort, never()).save(any());
    }

    @Test
    void shouldReturnNullFromAddItemWhenWishlistNotFound() {

        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(null);

        assertThat(manageWishlistUseCase.addItem(WISHLIST_ID, SKU, "name")).isNull();
        verify(saveWishlistOutPort, never()).save(any());
    }

    @Test
    void shouldRemoveExistingItem() {

        final WishlistItem existingItem = WishlistItem.builder()
                .sku(SKU)
                .productName("name")
                .addedDate(new java.util.Date())
                .build();
        final Wishlist existing = Wishlist.builder().wishlistId(WISHLIST_ID).items(List.of(existingItem)).build();
        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = manageWishlistUseCase.removeItem(WISHLIST_ID, SKU);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldNoOpWhenRemovingAbsentSku() {

        final Wishlist existing = Wishlist.builder().wishlistId(WISHLIST_ID).build();
        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);

        final Wishlist result = manageWishlistUseCase.removeItem(WISHLIST_ID, SKU);

        assertThat(result).isEqualTo(existing);
        verify(saveWishlistOutPort, never()).save(any());
    }

    @Test
    void shouldReturnNullFromRemoveItemWhenWishlistNotFound() {

        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(null);

        assertThat(manageWishlistUseCase.removeItem(WISHLIST_ID, SKU)).isNull();
    }

    @Test
    void shouldPreserveVersionAcrossMutations() {

        final Wishlist existing = Wishlist.builder().wishlistId(WISHLIST_ID).version(4).build();
        given(findWishlistOutPort.find(WISHLIST_ID)).willReturn(existing);
        final ArgumentCaptor<Wishlist> captor = ArgumentCaptor.forClass(Wishlist.class);
        given(saveWishlistOutPort.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        manageWishlistUseCase.addItem(WISHLIST_ID, SKU, "name");

        assertThat(captor.getValue().getVersion()).isEqualTo(4);
    }

}
