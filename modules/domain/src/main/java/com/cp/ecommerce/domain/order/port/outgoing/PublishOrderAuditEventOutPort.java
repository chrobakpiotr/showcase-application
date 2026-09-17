package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Outgoing port for publishing a lightweight order-audit event to a message queue. Implementations may send to AWS SQS or any
 * other messaging system. This is a best-effort secondary side-channel; failures must not affect the primary order flow.
 */
public interface PublishOrderAuditEventOutPort {

    /**
     * Publishes an audit event for the given order.
     *
     * @param order {@link Order} object that triggered the audit event.
     */
    void publish(Order order);

}
