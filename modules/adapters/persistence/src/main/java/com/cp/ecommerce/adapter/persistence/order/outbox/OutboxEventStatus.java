package com.cp.ecommerce.adapter.persistence.order.outbox;

/**
 * Status of outbox event.
 */
public enum OutboxEventStatus {

    PENDING,
    PROCESSING,
    SENT,
    COMPENSATING,
    COMPENSATED,
    CANCELLING,
    CANCELLED

}
