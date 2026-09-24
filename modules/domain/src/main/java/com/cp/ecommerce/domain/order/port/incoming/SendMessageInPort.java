package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;

/** Sends one logical fulfillment message under a stable replay identity. */
public interface SendMessageInPort {

    default OrderMessagePublishOutcome sendMessage(final Order order) {
        return sendMessage(order, "ORDER-FULFILLMENT:" + order.getOrderNumber());
    }

    OrderMessagePublishOutcome sendMessage(Order order, String operationId);
}
