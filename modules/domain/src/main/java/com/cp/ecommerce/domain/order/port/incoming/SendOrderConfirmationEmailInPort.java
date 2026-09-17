package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for sending the order confirmation email as part of the asynchronous order-placement saga (as opposed to
 * synchronously while the order is being placed).
 */
public interface SendOrderConfirmationEmailInPort {

    /**
     * Sends the order confirmation email for the given order.
     *
     * @param order {@link Order} that was placed.
     */
    void sendConfirmationEmail(Order order);

}
