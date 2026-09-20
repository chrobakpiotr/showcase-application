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
    default void send(final Order order) {

        send(order, "ORDER-FULFILLMENT:" + order.getOrderNumber());
    }

    /**
     * Sends one logical fulfillment command under a stable replay identity.
     *
     * @param order order to fulfill
     * @param operationId stable logical fulfillment identity
     */
    void send(Order order, String operationId);

}
