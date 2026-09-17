package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for publishing an order audit event to a secondary channel (e.g. SQS).
 */
public interface PublishOrderAuditEventInPort {

    /**
     * Publishes an audit event for the given order.
     *
     * @param order {@link Order} that was processed.
     */
    void publishAuditEvent(Order order);

}
