package com.cp.ecommerce.adapter.persistence.order;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.FindRecentOrdersByCustomerOutPort;

import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindRecentOrdersByCustomerOutPort} backed by {@link OrderEntityRepository}'s
 * {@code findTop5ByCustomerEmailAndCreatedAfterAndOrderNumberNotOrderByCreatedDesc} query (see ADR 0024): the candidate
 * customer is identified by email rather than {@code CUSTOMER.ID}, since every order gets its own {@code CustomerEntity} row
 * (see {@code OrderEntity#customer}, {@code CascadeType.ALL}) - email is the only stable identifier shared across a repeat
 * customer's orders.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindRecentOrdersByCustomerAdapter implements FindRecentOrdersByCustomerOutPort {

    private final OrderEntityRepository orderEntityRepository;

    private final OrderPersistenceMapper orderPersistenceMapper;

    /**
     * How far back to look for candidate siblings. Long enough to catch a customer who got confused and resubmitted a few
     * minutes later (e.g. after a slow page load or a browser back-button-and-resubmit), short enough that two genuinely
     * separate orders placed the same day don't get pulled into the candidate set just because they share a customer.
     */
    @Value("${service.ai.duplicate-order.lookback-minutes:15}")
    private int lookbackMinutes = 15;

    @Override
    public List<Order> findRecentOrders(final Order order) {

        final String customerEmail = order.getCustomer().getContact().getEmail();
        final Date createdAfter = Date.from(order.getCreated().toInstant().minus(Duration.ofMinutes(lookbackMinutes)));
        final List<OrderEntity> candidates = orderEntityRepository
                .findTop5ByCustomerEmailAndCreatedAfterAndOrderNumberNotOrderByCreatedDesc(
                        customerEmail,
                        createdAfter,
                        order.getOrderNumber());
        return candidates.stream().map(this::mapToDomainObjectOrThrow).toList();
    }

    private Order mapToDomainObjectOrThrow(final OrderEntity entity) {

        return orderPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map order entity to domain object for order number: " + entity.getOrderNumber()));
    }

}
