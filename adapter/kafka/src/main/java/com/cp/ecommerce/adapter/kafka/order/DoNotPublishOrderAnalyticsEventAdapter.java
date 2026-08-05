package com.cp.ecommerce.adapter.kafka.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAnalyticsEventOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link PublishOrderAnalyticsEventOutPort} with Kafka publishing disabled.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotPublishOrderAnalyticsEventAdapter implements PublishOrderAnalyticsEventOutPort {

    @Override
    public void publish(final Order order) {

        log.info("Kafka publishing disabled for order {}, analytics event will not be sent.", order.getOrderNumber());
    }

}
