package com.cp.ecommerce.adapter.persistence.order.analytics.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.analytics.OrderAnalyticsProjectionEntity;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link OrderAnalyticsProjection} object into/from entity object.
 */
@Component
public class OrderAnalyticsProjectionPersistenceMapper
        implements PersistenceMapper<OrderAnalyticsProjection, OrderAnalyticsProjectionEntity> {

    @Override
    public Optional<OrderAnalyticsProjectionEntity> mapToEntity(final OrderAnalyticsProjection projection) {

        return ofNullable(projection).map(
                domain -> OrderAnalyticsProjectionEntity.builder()
                        .orderNumber(domain.orderNumber())
                        .customerId(domain.customerId())
                        .orderPlacedDate(domain.orderPlacedDate())
                        .consumedDate(domain.consumedDate())
                        .build());
    }

    @Override
    public Optional<OrderAnalyticsProjection> mapToDomainObject(final OrderAnalyticsProjectionEntity entity) {

        return ofNullable(entity).map(
                e -> new OrderAnalyticsProjection(
                        e.getOrderNumber(),
                        e.getCustomerId(),
                        e.getOrderPlacedDate(),
                        e.getConsumedDate()));
    }

}
