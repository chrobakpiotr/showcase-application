package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for routing an order-placed notification to the appropriate fulfillment/notification channel(s) (e.g. via
 * Apache Camel).
 */
public interface RouteOrderNotificationInPort {

    /**
     * Routes a notification for the given order.
     *
     * @param order {@link Order} that was processed.
     */
    void routeNotification(Order order);

}
