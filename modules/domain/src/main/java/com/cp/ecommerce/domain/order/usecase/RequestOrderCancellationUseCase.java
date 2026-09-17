package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.adapter.common.exception.OrderNotCancellableException;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.incoming.RequestOrderCancellationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.CancelOrderOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case implementing a customer-initiated order cancellation request.
 *
 * <p>
 * Unlike {@link CancelOrderUseCase} - the order-placement saga's unconditional internal compensating transaction, invoked only
 * after fulfillment notification has exhausted its retries - this enforces the order's state machine: an order can only be
 * cancelled this way while it is still {@link OrderStatus#CONFIRMED}. Both use cases share the same {@link CancelOrderOutPort}
 * for the actual persistence-level state change, since "mark this order cancelled" is a single mechanism with two different
 * callers/guards.
 */
@RequiredArgsConstructor
@UseCase
public class RequestOrderCancellationUseCase implements RequestOrderCancellationInPort {

    private final FindOrderOutPort findOrderOutPort;

    private final CancelOrderOutPort cancelOrderOutPort;

    @Override
    public Order requestCancellation(final String orderNumber) {

        final Order order = findOrderOutPort.find(orderNumber);
        if (order == null) {

            return null;
        }
        if (!order.canBeCancelled()) {

            throw new OrderNotCancellableException(
                    "Order '" + orderNumber + "' cannot be cancelled: it is already " + order.getStatus());
        }
        cancelOrderOutPort.cancel(orderNumber);
        return findOrderOutPort.find(orderNumber);
    }

}
