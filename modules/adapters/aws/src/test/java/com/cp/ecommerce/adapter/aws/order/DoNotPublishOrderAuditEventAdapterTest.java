package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotPublishOrderAuditEventAdapter}.
 */
class DoNotPublishOrderAuditEventAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotPublishOrderAuditEventAdapter adapter = new DoNotPublishOrderAuditEventAdapter();
        final Order order = mockOrder();
        assertDoesNotThrow(() -> adapter.publish(order));
    }

}
