package com.cp.ecommerce.adapter.web.cart.mapper;

import com.cp.ecommerce.adapter.common.utils.CartBuilder;
import com.cp.ecommerce.domain.cart.Cart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link CartWebMapper}.
 */
class CartWebMapperTest {

    private final transient CartWebMapper cartWebMapper = new CartWebMapper();

    @Test
    void shouldMapToResource() {

        final Cart cart = CartBuilder.mockCart();

        final var result = cartWebMapper.mapToResource(cart);

        assertTrue(result.isPresent());
        assertEquals(cart.getCartId(), result.get().cartId());
        assertEquals(cart.getTotal(), result.get().total());
        assertEquals(cart.getItemCount(), result.get().itemCount());
        assertEquals(cart.getItems().size(), result.get().items().size());
        assertEquals(cart.getItems().getFirst().getSku(), result.get().items().getFirst().sku());
        assertEquals(cart.getItems().getFirst().getProductName(), result.get().items().getFirst().productName());
        assertEquals(cart.getItems().getFirst().getUnitPrice(), result.get().items().getFirst().unitPrice());
        assertEquals(cart.getItems().getFirst().getQuantity(), result.get().items().getFirst().quantity());
        assertEquals(cart.getItems().getFirst().getSubtotal(), result.get().items().getFirst().subtotal());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToResource() {

        assertTrue(cartWebMapper.mapToResource(null).isEmpty());
    }

}
