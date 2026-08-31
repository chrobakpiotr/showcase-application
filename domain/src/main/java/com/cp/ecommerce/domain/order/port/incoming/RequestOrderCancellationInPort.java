package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;

/**
 * Incoming port for a customer-initiated request to cancel an already-placed order.
 *
 * <p>
 * Unlike {@link CancelOrderInPort} - the order-placement saga's unconditional internal compensating transaction - this enforces
 * the order's state machine: only an order still in {@link com.cp.ecommerce.domain.order.OrderStatus#CONFIRMED} can be
 * cancelled this way.
 */
public interface RequestOrderCancellationInPort {

    /**
     * Cancels the order identified by the given order number, on behalf of a customer request.
     *
     * @param orderNumber number of the order to cancel.
     * @return the updated {@link Order}, with {@link com.cp.ecommerce.domain.order.OrderStatus#CANCELLED} status.
     * @throws com.cp.ecommerce.adapter.common.exception.OrderNotCancellableException if the order does not exist, or is no
     *             longer in a cancellable state.
     */
    Order requestCancellation(String orderNumber);

}
