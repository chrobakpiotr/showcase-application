package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Log order outgoing port.
 */
public interface LogOrderOutPort {

    /**
     * Log out placed order in JSON format.
     *
     * @param order placed order.
     */
    void log(final Order order);

}
