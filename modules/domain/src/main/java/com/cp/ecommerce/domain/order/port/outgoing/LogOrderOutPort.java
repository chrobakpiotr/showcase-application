package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Log order outgoing port.
 */
public interface LogOrderOutPort {

    /**
     * Emit a bounded, privacy-safe summary for the placed order.
     *
     * @param order placed order.
     */
    void log(final Order order);

}
