package com.cp.ecommerce.adapter.persistence.cart.mapper;

import com.cp.ecommerce.adapter.common.utils.CartBuilder;
import com.cp.ecommerce.adapter.persistence.utils.CartEntityBuilder;
import com.cp.ecommerce.domain.cart.Cart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link CartPersistenceMapper}.
 */
class CartPersistenceMapperTest {

    private final transient CartPersistenceMapper cartPersistenceMapper = new CartPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final Cart cart = CartBuilder.mockCart();

        final var result = cartPersistenceMapper.mapToEntity(cart);

        assertTrue(result.isPresent());
        assertEquals(cart.getCartId(), result.get().getCartId());
        assertEquals(cart.getUpdated(), result.get().getUpdated());
        assertEquals(cart.getVersion(), result.get().getVersion());
        assertEquals(cart.getItems().size(), result.get().getItems().size());
        assertEquals(cart.getItems().getFirst().getSku(), result.get().getItems().getFirst().getSku());
        assertEquals(cart.getItems().getFirst().getProductName(), result.get().getItems().getFirst().getProductName());
        assertEquals(cart.getItems().getFirst().getUnitPrice(), result.get().getItems().getFirst().getUnitPrice());
        assertEquals(cart.getItems().getFirst().getQuantity(), result.get().getItems().getFirst().getQuantity());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = CartEntityBuilder.mockCartEntity();

        final var result = cartPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getCartId(), result.get().getCartId());
        assertEquals(entity.getUpdated(), result.get().getUpdated());
        assertEquals(entity.getVersion(), result.get().getVersion());
        assertEquals(entity.getItems().size(), result.get().getItems().size());
        assertEquals(entity.getItems().getFirst().getSku(), result.get().getItems().getFirst().getSku());
        assertEquals(entity.getItems().getFirst().getProductName(), result.get().getItems().getFirst().getProductName());
        assertEquals(entity.getItems().getFirst().getUnitPrice(), result.get().getItems().getFirst().getUnitPrice());
        assertEquals(entity.getItems().getFirst().getQuantity(), result.get().getItems().getFirst().getQuantity());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(cartPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(cartPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
