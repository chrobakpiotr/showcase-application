package com.cp.ecommerce.domain.order;

/** Outcome of atomically recording one logical fulfillment message receipt. */
public enum OrderFulfillmentReceiptOutcome {
    RECORDED,
    REPLAYED
}
