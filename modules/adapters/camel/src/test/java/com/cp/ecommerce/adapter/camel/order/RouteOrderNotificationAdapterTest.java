package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.camel.order.OrderNotificationRoutes.ORDER_NOTIFICATION_ENDPOINT;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link RouteOrderNotificationAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class RouteOrderNotificationAdapterTest {

    @Mock
    transient ProducerTemplate producerTemplate;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldSendOrderToCamelRoute() {

        final Order order = mockOrder();
        final RouteOrderNotificationAdapter adapter = new RouteOrderNotificationAdapter(producerTemplate, resilientExecutor);
        runResilientActionEagerly();

        adapter.route(order);

        verify(producerTemplate).sendBody(ORDER_NOTIFICATION_ENDPOINT, order);
    }

    private void runResilientActionEagerly() {

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

}
