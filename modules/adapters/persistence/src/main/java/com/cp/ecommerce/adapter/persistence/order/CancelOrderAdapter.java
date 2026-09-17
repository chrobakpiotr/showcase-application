package com.cp.ecommerce.adapter.persistence.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.outgoing.CancelOrderOutPort;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link CancelOrderOutPort}: the persistence side of the order-placement saga's compensating transaction.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
class CancelOrderAdapter implements CancelOrderOutPort {

    private final OrderEntityRepository orderEntityRepository;

    @Override
    public void cancel(final String orderNumber) {

        final OrderEntity orderEntity = Optional.ofNullable(orderEntityRepository.getOrderEntityByOrderNumber(orderNumber))
                .orElseThrow(() -> new IllegalStateException("Failed to find order entity for order number: " + orderNumber));
        orderEntity.setStatus(OrderStatus.CANCELLED);
        orderEntityRepository.save(orderEntity);
    }

}
