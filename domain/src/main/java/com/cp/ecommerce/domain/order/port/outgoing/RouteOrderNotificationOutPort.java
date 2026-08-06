package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Outgoing port for routing a placed-order notification to the appropriate fulfillment channel(s) using content-based routing
 * (e.g. domestic vs. international shipping), with a wire-tapped copy kept for auditing. Implementations may use Apache Camel
 * or any other integration/mediation engine. This is a best-effort secondary side-channel; failures must not affect the primary
 * order flow.
 */
public interface RouteOrderNotificationOutPort {

    /**
     * Routes a notification for the given order to the appropriate fulfillment channel(s).
     *
     * @param order {@link Order} that triggered the routing.
     */
    void route(Order order);

}
