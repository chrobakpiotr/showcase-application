package com.cp.ecommerce.adapter.persistence.cart;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntityRepository;
import com.cp.ecommerce.adapter.persistence.cart.mapper.CartPersistenceMapper;
import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.port.outgoing.FindCartOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindCartOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindCartAdapter implements FindCartOutPort {

    private final CartEntityRepository cartEntityRepository;

    private final CartPersistenceMapper cartPersistenceMapper;

    @Override
    public Cart find(final String cartId) {

        return cartEntityRepository.findById(cartId).flatMap(cartPersistenceMapper::mapToDomainObject).orElse(null);
    }

}
