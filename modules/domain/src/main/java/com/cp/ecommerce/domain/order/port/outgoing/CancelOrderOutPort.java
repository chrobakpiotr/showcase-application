package com.cp.ecommerce.domain.order.port.outgoing;

/**
 * Cancel order outgoing port.
 */
public interface CancelOrderOutPort {

    /**
     * Marks the order identified by the given order number as cancelled.
     *
     * @param orderNumber number of the order to cancel.
     */
    void cancel(String orderNumber);

}
