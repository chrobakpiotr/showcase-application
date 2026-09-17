package com.cp.ecommerce.adapter.persistence.cart;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.exception.CartConflictException;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntity;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntityRepository;
import com.cp.ecommerce.adapter.persistence.cart.mapper.CartPersistenceMapper;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.port.outgoing.SaveCartOutPort;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveCartOutPort}.
 *
 * <p>
 * Uses {@code saveAndFlush} rather than plain {@code save}, exactly like {@code SaveStockLevelAdapter} (ADR 0026): it forces
 * the version-checked {@code UPDATE} to execute (and any conflict to surface) synchronously within this method rather than
 * whenever the surrounding transaction happens to flush.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class SaveCartAdapter implements SaveCartOutPort {

    private final CartEntityRepository cartEntityRepository;

    private final CartPersistenceMapper cartPersistenceMapper;

    @Override
    public Cart save(final Cart cart) {

        final CartEntity entityToSave = cartPersistenceMapper.mapToEntity(cart)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map cart domain object to entity for id: " + cart.getCartId()));
        try {

            final CartEntity saved = cartEntityRepository.saveAndFlush(entityToSave);
            return cartPersistenceMapper.mapToDomainObject(saved)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Failed to map cart entity to domain object for id: " + cart.getCartId()));
        } catch (final OptimisticLockingFailureException conflict) {

            throw new CartConflictException(cart.getCartId(), conflict);
        }
    }

}
