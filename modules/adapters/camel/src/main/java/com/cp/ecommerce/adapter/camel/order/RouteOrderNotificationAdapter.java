package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.RouteOrderNotificationOutPort;

import org.apache.camel.ProducerTemplate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.cp.ecommerce.adapter.camel.order.OrderNotificationRoutes.ORDER_NOTIFICATION_ENDPOINT;

@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.camel.enabled", havingValue = "true")
public class RouteOrderNotificationAdapter implements RouteOrderNotificationOutPort {

    private final ProducerTemplate producerTemplate;

    @Override
    public void route(final Order order) {
        log.info("Routing order notification through Camel: orderNumber={}", order.getOrderNumber());
        producerTemplate.sendBody(ORDER_NOTIFICATION_ENDPOINT, order);
    }
}
