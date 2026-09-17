package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.RouteOrderNotificationOutPort;

import org.apache.camel.ProducerTemplate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.cp.ecommerce.adapter.camel.order.OrderNotificationRoutes.ORDER_NOTIFICATION_ENDPOINT;

/**
 * Implementation of {@link RouteOrderNotificationOutPort} that hands the order off to the Camel route defined in
 * {@link OrderNotificationRoutes} for content-based fan-out (wire-tapped audit copy, then domestic/international routing),
 * wrapped behind a circuit-breaker and retry.
 */
@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.camel.enabled", havingValue = "true")
public class RouteOrderNotificationAdapter implements RouteOrderNotificationOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "routeOrderNotification";

    private final ProducerTemplate producerTemplate;

    private final ResilientExecutor resilientExecutor;

    @Override
    public void route(final Order order) {

        log.info("Routing order notification through Camel: orderNumber={}", order.getOrderNumber());
        resilientExecutor
                .runResilient(RESILIENCE_INSTANCE_NAME, () -> producerTemplate.sendBody(ORDER_NOTIFICATION_ENDPOINT, order));
    }

}
