package com.cp.ecommerce.domain.order.port.incoming;

/**
 * Incoming port for cancelling an order. This is the compensating transaction of the order-placement saga: it is invoked once
 * the saga's fulfillment-notification step has exhausted its retries, rolling the order back to a
 * {@link com.cp.ecommerce.domain.order.OrderStatus#CANCELLED} state instead of leaving it stuck as confirmed forever.
 */
public interface CancelOrderInPort {

    /**
     * Cancels the order identified by the given order number.
     *
     * @param orderNumber number of the order to cancel.
     */
    void cancelOrder(String orderNumber);

}
