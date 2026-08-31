package com.cp.ecommerce.adapter.persistence.order.analytics.mapper;

import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.OrderAnalyticsProjectionEntity;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OrderAnalyticsProjectionPersistenceMapper} mapper test.
 */
class OrderAnalyticsProjectionPersistenceMapperTest {

    private static final String TEST_ORDER_NUMBER = "ORDER-1";

    private final transient OrderAnalyticsProjectionPersistenceMapper orderAnalyticsProjectionPersistenceMapper = new OrderAnalyticsProjectionPersistenceMapper();

    @Test
    void shouldMapDomainToEntity() {

        final Date orderPlacedDate = new Date();
        final Date consumedDate = new Date();
        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                TEST_ORDER_NUMBER,
                1L,
                orderPlacedDate,
                consumedDate);

        final Optional<OrderAnalyticsProjectionEntity> result = orderAnalyticsProjectionPersistenceMapper
                .mapToEntity(projection);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isNull();
        assertThat(result.get().getOrderNumber()).isEqualTo(TEST_ORDER_NUMBER);
        assertThat(result.get().getCustomerId()).isEqualTo(1L);
        assertThat(result.get().getOrderPlacedDate()).isEqualTo(orderPlacedDate);
        assertThat(result.get().getConsumedDate()).isEqualTo(consumedDate);
    }

    @Test
    void shouldMapEntityToDomain() {

        final Date orderPlacedDate = new Date();
        final Date consumedDate = new Date();
        final OrderAnalyticsProjectionEntity entity = OrderAnalyticsProjectionEntity.builder()
                .id(1L)
                .orderNumber(TEST_ORDER_NUMBER)
                .customerId(1L)
                .orderPlacedDate(orderPlacedDate)
                .consumedDate(consumedDate)
                .build();

        final Optional<OrderAnalyticsProjection> result = orderAnalyticsProjectionPersistenceMapper.mapToDomainObject(entity);

        assertThat(result).contains(new OrderAnalyticsProjection(TEST_ORDER_NUMBER, 1L, orderPlacedDate, consumedDate));
    }

    @Test
    void shouldMapNullEntityToEmptyOptional() {

        final Optional<OrderAnalyticsProjection> result = orderAnalyticsProjectionPersistenceMapper.mapToDomainObject(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMapNullDomainObjectToEmptyOptional() {

        final Optional<OrderAnalyticsProjectionEntity> result = orderAnalyticsProjectionPersistenceMapper.mapToEntity(null);
        assertTrue(result.isEmpty());
    }

}
