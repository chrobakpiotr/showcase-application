package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.OrderFulfillmentReceiptOutcome;
import com.cp.ecommerce.domain.order.OrderMessage;

/** Atomically records one durable fulfillment receipt or resolves an idempotent replay. */
public interface SaveOrderFulfillmentReceiptOutPort {

    OrderFulfillmentReceiptOutcome saveOnce(OrderMessage message);
}
