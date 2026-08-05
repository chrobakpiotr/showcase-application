package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for publishing an order-placed event to the analytics event stream (e.g. Kafka).
 */
public interface PublishOrderAnalyticsEventInPort {

    /**
     * Publishes an analytics event for the given order.
     *
     * @param order {@link Order} that was processed.
     */
    void publishAnalyticsEvent(Order order);

}
