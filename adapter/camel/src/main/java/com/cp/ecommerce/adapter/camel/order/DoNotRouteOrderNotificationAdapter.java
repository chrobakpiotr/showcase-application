package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.RouteOrderNotificationOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link RouteOrderNotificationOutPort} with Camel-based routing disabled.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.camel.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotRouteOrderNotificationAdapter implements RouteOrderNotificationOutPort {

    @Override
    public void route(final Order order) {

        log.info("Camel routing disabled for order, notification will not be routed.");
    }

}
