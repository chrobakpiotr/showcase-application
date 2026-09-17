package com.cp.ecommerce.adapter.persistence.cart;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CartBuilder;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntityRepository;
import com.cp.ecommerce.adapter.persistence.cart.mapper.CartPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.CartEntityBuilder;
import com.cp.ecommerce.domain.cart.Cart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_ID;

/**
 * Test class for {@link FindCartAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindCartAdapterTest {

    @InjectMocks
    private transient FindCartAdapter findCartAdapter;

    @Mock
    private transient CartEntityRepository cartEntityRepository;

    @Mock
    private transient CartPersistenceMapper cartPersistenceMapper;

    @Test
    void shouldFindCartById() {

        final var entity = CartEntityBuilder.mockCartEntity();
        final Cart cart = CartBuilder.mockCart();
        doReturn(Optional.of(entity)).when(cartEntityRepository).findById(TEST_CART_ID);
        doReturn(Optional.of(cart)).when(cartPersistenceMapper).mapToDomainObject(entity);

        final Cart result = findCartAdapter.find(TEST_CART_ID);

        assertEquals(cart, result);
    }

    @Test
    void shouldReturnNullWhenCartNotFound() {

        doReturn(Optional.empty()).when(cartEntityRepository).findById(TEST_CART_ID);

        final Cart result = findCartAdapter.find(TEST_CART_ID);

        assertNull(result);
    }

}
