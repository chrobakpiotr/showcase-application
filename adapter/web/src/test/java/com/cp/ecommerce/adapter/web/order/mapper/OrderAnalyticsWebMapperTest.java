package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.web.order.resource.OrderAnalyticsResource;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the order-analytics mapper behavior.
 */
@ExtendWith(MockitoExtension.class)
class OrderAnalyticsWebMapperTest {

    @InjectMocks
    private transient OrderAnalyticsWebMapper orderAnalyticsWebMapper;

    @Test
    void shouldReturnEmptyIfNull() {

        final Optional<OrderAnalyticsResource> resource = orderAnalyticsWebMapper.mapToResource(null);

        assertFalse(resource.isPresent());
    }

    @Test
    void shouldMapProjectionToResource() {

        final Date orderPlacedDate = new Date();
        final Date consumedDate = new Date();
        final OrderAnalyticsProjection projection = new OrderAnalyticsProjection(
                "ORD-1001",
                1001L,
                orderPlacedDate,
                consumedDate);

        final Optional<OrderAnalyticsResource> resource = orderAnalyticsWebMapper.mapToResource(projection);

        assertTrue(resource.isPresent());
        assertThat(resource.get().orderNumber()).isEqualTo("ORD-1001");
        assertThat(resource.get().customerId()).isEqualTo(1001L);
        assertThat(resource.get().orderPlacedDate()).isEqualTo(orderPlacedDate);
        assertThat(resource.get().consumedDate()).isEqualTo(consumedDate);
    }

}
