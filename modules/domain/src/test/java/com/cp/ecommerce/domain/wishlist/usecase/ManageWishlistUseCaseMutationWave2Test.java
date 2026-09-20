package com.cp.ecommerce.domain.wishlist.usecase;

import java.util.List;

import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;
import com.cp.ecommerce.domain.wishlist.port.outgoing.FindWishlistOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.GenerateWishlistIdOutPort;
import com.cp.ecommerce.domain.wishlist.port.outgoing.SaveWishlistOutPort;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageWishlistUseCaseMutationWave2Test {

    private static final String ID = "WISH-1";

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
    void shouldValidateGeneratedWishlistBeforeSaving() {
        given(generateWishlistIdOutPort.generate()).willReturn(null);

        assertThatThrownBy(useCase::createWishlist).isInstanceOf(DomainObjectValidationException.class);
        verify(saveWishlistOutPort, never()).save(any());
    }

    @Test
    void shouldValidateNestedItemBeforeSavingMutation() {
        final Wishlist existing = Wishlist.builder().wishlistId(ID).version(5).build();
        given(findWishlistOutPort.find(ID)).willReturn(existing);

        assertThatThrownBy(() -> useCase.addItem(ID, "SKU-1", " ")).isInstanceOf(DomainObjectValidationException.class);
        verify(saveWishlistOutPort, never()).save(any());
    }

    @Test
    void shouldPersistAddedItemTimestampAndWishlistVersion() {
        final Wishlist existing = Wishlist.builder().wishlistId(ID).version(5).build();
        given(findWishlistOutPort.find(ID)).willReturn(existing);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = useCase.addItem(ID, "SKU-1", "Product");

        assertThat(result.getVersion()).isEqualTo(5);
        assertThat(result.getUpdated()).isNotNull();
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSku()).isEqualTo("SKU-1");
            assertThat(item.getProductName()).isEqualTo("Product");
            assertThat(item.getAddedDate()).isNotNull();
        });
    }

    @Test
    void shouldPersistRemovalAndPreserveVersion() {
        final WishlistItem item = WishlistItem.builder()
                .sku("SKU-1")
                .productName("Product")
                .addedDate(java.time.Instant.parse("2026-09-20T10:00:00Z"))
                .build();
        final Wishlist existing = Wishlist.builder().wishlistId(ID).items(List.of(item)).version(5).build();
        given(findWishlistOutPort.find(ID)).willReturn(existing);
        given(saveWishlistOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Wishlist result = useCase.removeItem(ID, "SKU-1");

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getVersion()).isEqualTo(5);
        assertThat(result.getUpdated()).isNotNull();
    }
}
