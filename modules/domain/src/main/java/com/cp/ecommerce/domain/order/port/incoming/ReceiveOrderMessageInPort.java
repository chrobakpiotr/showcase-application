package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;

/** Processes one RabbitMQ order-fulfillment message under its stable operation identity. */
public interface ReceiveOrderMessageInPort {

    OrderFulfillmentReceiptOutcome receive(OrderMessage message);
}
