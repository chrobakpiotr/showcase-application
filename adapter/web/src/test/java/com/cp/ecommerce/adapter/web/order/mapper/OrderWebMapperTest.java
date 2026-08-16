package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.adapter.web.utils.OrderResourceBuilder;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the order mapper behavior.
 */
@ExtendWith(MockitoExtension.class)
class OrderWebMapperTest {

    @InjectMocks
    private transient OrderWebMapper orderWebMapper;

    @Test
    public void shouldReturnEmptyIfNull() {

        final Optional<Order> order = orderWebMapper.mapToDomainObject(null);

        assertFalse(order.isPresent());
    }

    @Test
    void shouldMapOrder() {

        final OrderResource orderResource = OrderResourceBuilder.mockOrderResource();
        final Optional<Order> order = orderWebMapper.mapToDomainObject(orderResource);

        assertTrue(order.isPresent());
        assertThat(order.get().getRemarks()).isEqualTo(orderResource.remarks());
    }

    @Test
    public void shouldReturnEmptyIfNullWhileMapToResource() {

        final Optional<OrderResource> orderResource = orderWebMapper.mapToResource(null);
        assertFalse(orderResource.isPresent());
    }

}
