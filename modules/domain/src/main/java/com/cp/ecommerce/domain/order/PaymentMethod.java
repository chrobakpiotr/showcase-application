package com.cp.ecommerce.domain.order;

/**
 * How a customer chose to pay for an {@link Order}, captured at placement time as one of the order's own attributes -
 * conceptually no different from {@link #toString()}-able fields like {@code remarks}: it records what the customer asked for,
 * not the outcome of actually charging them, which is the {@code payment} bounded context's own concern (see ADR 0030). Kept
 * here rather than in {@code domain.payment} so that {@code Order} never needs to depend on the payment bounded context's
 * domain model - the same "no cross-context domain dependency" stance already taken for catalog/inventory/cart (see ADR 0026).
 */
public enum PaymentMethod {

    CARD,

    PAYPAL,

    BANK_TRANSFER

}
