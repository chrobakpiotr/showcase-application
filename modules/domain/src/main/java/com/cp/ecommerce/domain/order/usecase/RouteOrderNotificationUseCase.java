package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.RouteOrderNotificationOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for routing a best-effort order-placed notification to its fulfillment channel(s).
 */
@RequiredArgsConstructor
@UseCase
public class RouteOrderNotificationUseCase implements RouteOrderNotificationInPort {

    private final RouteOrderNotificationOutPort routeOrderNotificationOutPort;

    @Override
    public void routeNotification(final Order order) {

        routeOrderNotificationOutPort.route(order);
    }

}
