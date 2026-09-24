package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.domain.order.Order;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.camel.order.OrderNotificationRoutes.ORDER_NOTIFICATION_ENDPOINT;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

class RouteOrderNotificationAdapterTest {

    @Test
    void shouldSendOneOrderToCamelRoute() {
        final Order order = mockOrder();
        final ProducerTemplate template = mock(ProducerTemplate.class);
        new RouteOrderNotificationAdapter(template).route(order);
        verify(template).sendBody(ORDER_NOTIFICATION_ENDPOINT, order);
    }

    @Test
    void shouldPropagateFailureForDurableRetry() {
        final Order order = mockOrder();
        final ProducerTemplate template = mock(ProducerTemplate.class);
        doThrow(new IllegalStateException("route unavailable")).when(template).sendBody(ORDER_NOTIFICATION_ENDPOINT, order);
        assertThatThrownBy(() -> new RouteOrderNotificationAdapter(template).route(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("route unavailable");
    }
}
