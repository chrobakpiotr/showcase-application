package com.cp.ecommerce.adapter.persistence.order.idempotency;

/**
 * Status of a persisted {@link IdempotencyKeyEntity}.
 */
public enum IdempotencyKeyStatus {

    /**
     * A request for this key is currently being processed; no result is available to replay yet.
     */
    IN_PROGRESS,

    /**
     * The request for this key finished successfully; {@link IdempotencyKeyEntity#getOrderNumber()} can be replayed.
     */
    COMPLETED

}
