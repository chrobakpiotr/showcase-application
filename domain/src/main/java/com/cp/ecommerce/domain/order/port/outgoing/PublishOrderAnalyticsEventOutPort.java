package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Outgoing port for publishing an order-placed event onto the analytics event stream (Kafka). Unlike
 * {@link SendOrderMessageOutPort} (a point-to-point fulfillment command consumed by a single downstream processor), this event
 * is a fan-out broadcast: any number of independent consumers (BI/analytics pipelines, recommendation engine, customer
 * behaviour tracking, ...) can subscribe to it and replay it without coordinating with the producer or with each other. This is
 * a best-effort side-channel; failures must not affect the primary order flow.
 */
public interface PublishOrderAnalyticsEventOutPort {

    /**
     * Publishes an order-placed analytics event for the given order.
     *
     * @param order {@link Order} that triggered the event.
     */
    void publish(Order order);

}
