package com.cp.ecommerce.domain.order;

import java.util.Date;

/**
 * Read-model projection built from the Kafka order-analytics event stream (see {@code OrderAnalyticsEventConsumer} in
 * adapter:kafka), completing the producer-only pipeline documented by {@code publishOrderAnalytics} in
 * {@code etc/asyncapi/asyncapi.yml} with an actual in-process consumer.
 *
 * <p>
 * Deliberately an insert-only "recent orders" log rather than a per-day aggregated count: an aggregate would need an upsert
 * keyed by date, which - like {@code FindSequenceNumberOutPort}'s H2/Postgres split - would need a database-specific
 * implementation. Inserting one row per consumed event avoids that entirely at the cost of unbounded growth, an accepted
 * trade-off for a showcase read-model.
 *
 * @param orderNumber business-facing order identifier the event was published for.
 * @param customerId identifier of the customer who placed the order.
 * @param orderPlacedDate timestamp the order was originally placed, as carried by the event.
 * @param consumedDate timestamp this consumer processed the event, useful to observe consumer lag.
 */
public record OrderAnalyticsProjection(String orderNumber, Long customerId, Date orderPlacedDate, Date consumedDate) {

}
