package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotRouteOrderNotificationAdapter}.
 */
class DoNotRouteOrderNotificationAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotRouteOrderNotificationAdapter adapter = new DoNotRouteOrderNotificationAdapter();
        final Order order = mockOrder();
        assertDoesNotThrow(() -> adapter.route(order));
    }

}
