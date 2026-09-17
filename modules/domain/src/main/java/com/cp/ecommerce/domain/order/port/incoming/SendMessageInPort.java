package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Send message incoming port.
 */
public interface SendMessageInPort {

    /**
     * This method is meant to trigger sending of relevant message to queue regarding placed order.
     *
     * @param order domain {@link Order} class.
     */
    void sendMessage(Order order);
}
