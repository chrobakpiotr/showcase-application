package com.cp.ecommerce.adapter.persistence.cart;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.CartConflictException;
import com.cp.ecommerce.adapter.common.utils.CartBuilder;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntity;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntityRepository;
import com.cp.ecommerce.adapter.persistence.cart.mapper.CartPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.CartEntityBuilder;
import com.cp.ecommerce.domain.cart.Cart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Test class for {@link SaveCartAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveCartAdapterTest {

    @InjectMocks
    private transient SaveCartAdapter saveCartAdapter;

    @Mock
    private transient CartEntityRepository cartEntityRepository;

    @Mock
    private transient CartPersistenceMapper cartPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedCart() {

        final Cart cart = CartBuilder.mockCart();
        final CartEntity mappedEntity = CartEntityBuilder.mockCartEntity();
        doReturn(Optional.of(mappedEntity)).when(cartPersistenceMapper).mapToEntity(eq(cart));
        doReturn(mappedEntity).when(cartEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.of(cart)).when(cartPersistenceMapper).mapToDomainObject(mappedEntity);

        final Cart result = saveCartAdapter.save(cart);

        assertEquals(cart, result);
    }

    @Test
    void shouldThrowCartConflictExceptionWhenOptimisticLockFails() {

        final Cart cart = CartBuilder.mockCart();
        final CartEntity mappedEntity = CartEntityBuilder.mockCartEntity();
        doReturn(Optional.of(mappedEntity)).when(cartPersistenceMapper).mapToEntity(eq(cart));
        doThrow(new OptimisticLockingFailureException("conflict")).when(cartEntityRepository).saveAndFlush(mappedEntity);

        assertThrows(CartConflictException.class, () -> saveCartAdapter.save(cart));
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Cart cart = CartBuilder.mockCart();
        doReturn(Optional.empty()).when(cartPersistenceMapper).mapToEntity(eq(cart));

        assertThrows(IllegalStateException.class, () -> saveCartAdapter.save(cart));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Cart cart = CartBuilder.mockCart();
        final CartEntity mappedEntity = CartEntityBuilder.mockCartEntity();
        doReturn(Optional.of(mappedEntity)).when(cartPersistenceMapper).mapToEntity(eq(cart));
        doReturn(mappedEntity).when(cartEntityRepository).saveAndFlush(mappedEntity);
        doReturn(Optional.empty()).when(cartPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveCartAdapter.save(cart));
    }

}
