package com.cp.ecommerce.adapter.kafka.order;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotPublishOrderAnalyticsEventAdapter}.
 */
class DoNotPublishOrderAnalyticsEventAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotPublishOrderAnalyticsEventAdapter adapter = new DoNotPublishOrderAnalyticsEventAdapter();
        final Order order = mockOrder();
        assertDoesNotThrow(() -> adapter.publish(order));
    }

}
