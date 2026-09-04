package com.cp.ecommerce.domain.cart.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;
import com.cp.ecommerce.domain.cart.port.outgoing.FindCartOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.GenerateCartIdOutPort;
import com.cp.ecommerce.domain.cart.port.outgoing.SaveCartOutPort;

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
 * Tests for {@link ManageCartUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageCartUseCaseTest {

    private static final String CART_ID = "CART-1";

    private static final String SKU = "SKU-1";

    @InjectMocks
    private transient ManageCartUseCase manageCartUseCase;

    @Mock
    private transient FindCartOutPort findCartOutPort;

    @Mock
    private transient SaveCartOutPort saveCartOutPort;

    @Mock
    private transient GenerateCartIdOutPort generateCartIdOutPort;

    @Test
    void shouldCreateEmptyCartWithGeneratedId() {

        given(generateCartIdOutPort.generate()).willReturn(CART_ID);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.createCart();

        assertThat(result.getCartId()).isEqualTo(CART_ID);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldReturnCartFromFindCartOutPort() {

        final Cart cart = Cart.builder().cartId(CART_ID).build();
        given(findCartOutPort.find(CART_ID)).willReturn(cart);

        assertThat(manageCartUseCase.getCart(CART_ID)).isEqualTo(cart);
    }

    @Test
    void shouldReturnNullFromGetCartWhenNotFound() {

        given(findCartOutPort.find(CART_ID)).willReturn(null);

        assertThat(manageCartUseCase.getCart(CART_ID)).isNull();
    }

    @Test
    void shouldAddNewLineItemToEmptyCart() {

        final Cart existing = Cart.builder().cartId(CART_ID).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.addItem(CART_ID, SKU, "name", BigDecimal.TEN, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void shouldMergeQuantityWhenAddingExistingSku() {

        final CartLineItem existingItem = CartLineItem.builder()
                .sku(SKU)
                .productName("old")
                .unitPrice(BigDecimal.ONE)
                .quantity(3)
                .build();
        final Cart existing = Cart.builder().cartId(CART_ID).items(List.of(existingItem)).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.addItem(CART_ID, SKU, "new name", BigDecimal.TEN, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(result.getItems().get(0).getProductName()).isEqualTo("new name");
        assertThat(result.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10");
    }

    @Test
    void shouldReturnNullFromAddItemWhenCartNotFound() {

        given(findCartOutPort.find(CART_ID)).willReturn(null);

        assertThat(manageCartUseCase.addItem(CART_ID, SKU, "name", BigDecimal.TEN, 1)).isNull();
        verify(saveCartOutPort, never()).save(any());
    }

    @Test
    void shouldUpdateItemQuantity() {

        final CartLineItem existingItem = CartLineItem.builder()
                .sku(SKU)
                .productName("name")
                .unitPrice(BigDecimal.TEN)
                .quantity(2)
                .build();
        final Cart existing = Cart.builder().cartId(CART_ID).items(List.of(existingItem)).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.updateItemQuantity(CART_ID, SKU, 7);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(7);
    }

    @Test
    void shouldNoOpWhenUpdatingQuantityOfAbsentSku() {

        final Cart existing = Cart.builder().cartId(CART_ID).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);

        final Cart result = manageCartUseCase.updateItemQuantity(CART_ID, SKU, 7);

        assertThat(result).isEqualTo(existing);
        verify(saveCartOutPort, never()).save(any());
    }

    @Test
    void shouldReturnNullFromUpdateItemQuantityWhenCartNotFound() {

        given(findCartOutPort.find(CART_ID)).willReturn(null);

        assertThat(manageCartUseCase.updateItemQuantity(CART_ID, SKU, 1)).isNull();
    }

    @Test
    void shouldRemoveExistingLineItem() {

        final CartLineItem existingItem = CartLineItem.builder()
                .sku(SKU)
                .productName("name")
                .unitPrice(BigDecimal.TEN)
                .quantity(2)
                .build();
        final Cart existing = Cart.builder().cartId(CART_ID).items(List.of(existingItem)).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.removeItem(CART_ID, SKU);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldNoOpWhenRemovingAbsentSku() {

        final Cart existing = Cart.builder().cartId(CART_ID).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.removeItem(CART_ID, SKU);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldReturnNullFromRemoveItemWhenCartNotFound() {

        given(findCartOutPort.find(CART_ID)).willReturn(null);

        assertThat(manageCartUseCase.removeItem(CART_ID, SKU)).isNull();
    }

    @Test
    void shouldClearAllLineItems() {

        final CartLineItem existingItem = CartLineItem.builder()
                .sku(SKU)
                .productName("name")
                .unitPrice(BigDecimal.TEN)
                .quantity(2)
                .build();
        final Cart existing = Cart.builder().cartId(CART_ID).items(List.of(existingItem)).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        given(saveCartOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Cart result = manageCartUseCase.clearCart(CART_ID);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldReturnNullFromClearCartWhenCartNotFound() {

        given(findCartOutPort.find(CART_ID)).willReturn(null);

        assertThat(manageCartUseCase.clearCart(CART_ID)).isNull();
    }

    @Test
    void shouldPreserveVersionAcrossMutations() {

        final Cart existing = Cart.builder().cartId(CART_ID).version(4).build();
        given(findCartOutPort.find(CART_ID)).willReturn(existing);
        final ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        given(saveCartOutPort.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        manageCartUseCase.addItem(CART_ID, SKU, "name", BigDecimal.TEN, 1);

        assertThat(captor.getValue().getVersion()).isEqualTo(4);
    }

}
