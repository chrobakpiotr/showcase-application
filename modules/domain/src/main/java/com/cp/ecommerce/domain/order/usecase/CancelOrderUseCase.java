package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.CancelOrderOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case implementing the order-placement saga's compensating transaction: cancelling an order that could not be completed
 * end-to-end.
 */
@RequiredArgsConstructor
@UseCase
public class CancelOrderUseCase implements CancelOrderInPort {

    private final CancelOrderOutPort cancelOrderOutPort;

    @Override
    public void cancelOrder(final String orderNumber) {

        cancelOrderOutPort.cancel(orderNumber);
    }

}
