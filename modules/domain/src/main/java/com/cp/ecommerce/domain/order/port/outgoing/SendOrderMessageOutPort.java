package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

/**
 * Send order message outgoing port.
 */
public interface SendOrderMessageOutPort {

    /**
     * Sending email.
     *
     * @param order {@link Order} object.
     */
    void send(final Order order);

}
