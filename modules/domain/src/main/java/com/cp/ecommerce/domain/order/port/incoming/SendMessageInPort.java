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
    default void sendMessage(final Order order) {

        sendMessage(order, "ORDER-FULFILLMENT:" + order.getOrderNumber());
    }

    /**
     * Sends one logical fulfillment command under a stable replay identity.
     *
     * @param order order to fulfill
     * @param operationId stable logical fulfillment identity
     */
    void sendMessage(Order order, String operationId);
}
