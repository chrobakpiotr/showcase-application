package com.cp.ecommerce.adapter.persistence.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindOrderOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindOrderAdapter implements FindOrderOutPort {

    private final OrderPersistenceMapper orderPersistenceMapper;

    private final OrderEntityRepository orderEntityRepository;

    @Override
    public Order find(final String orderNumber) {

        return Optional.ofNullable(orderEntityRepository.getOrderEntityByOrderNumber(orderNumber))
                .map(
                        order -> orderPersistenceMapper.mapToDomainObject(order)
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "Failed to map order entity to domain object for order number: "
                                                        + orderNumber)))
                .orElse(null);
    }

}
