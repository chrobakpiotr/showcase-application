package com.cp.ecommerce.adapter.persistence.order;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderOutPort;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link Order} persistence functionality.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
public class SaveOrderAdapter implements SaveOrderOutPort {

    private final OrderPersistenceMapper orderPersistenceMapper;

    private final OrderEntityRepository orderEntityRepository;

    private final OutboxEventEntityRepository outboxEventEntityRepository;

    @Override
    public Order save(final Order order) {

        orderEntityRepository.save(
                orderPersistenceMapper.mapToEntity(order)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Failed to map order domain object to entity for order number: "
                                                + order.getOrderNumber())));
        outboxEventEntityRepository.save(
                OutboxEventEntity.builder()
                        .orderNumber(order.getOrderNumber())
                        .status(OutboxEventStatus.PENDING)
                        .createdDate(new Date())
                        .build());
        return order;
    }

}
