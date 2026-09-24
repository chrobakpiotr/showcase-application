package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;

/** Publishes one logical fulfillment command under a stable replay identity. */
public interface SendOrderMessageOutPort {

    default OrderMessagePublishOutcome send(final Order order) {
        return send(order, "ORDER-FULFILLMENT:" + order.getOrderNumber());
    }

    OrderMessagePublishOutcome send(Order order, String operationId);
}
