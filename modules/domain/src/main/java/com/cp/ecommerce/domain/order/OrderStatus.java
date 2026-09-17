package com.cp.ecommerce.domain.order;

/**
 * Lifecycle status of an {@link Order}.
 */
public enum OrderStatus {

    /**
     * Order was placed and durably saved; this is the default/initial status.
     */
    CONFIRMED,

    /**
     * Order placement could not be completed end-to-end and was rolled back by the order-placement saga's compensating
     * transaction (see {@code OrderPlacementSagaOrchestrator}).
     */
    CANCELLED

}
