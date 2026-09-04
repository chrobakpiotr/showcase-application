package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindSequenceNumberOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOrderNumberOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for managing order.
 */
@UseCase
@RequiredArgsConstructor
public class ManageOrderUseCase implements ManageOrderInPort {

    private final SaveOrderOutPort saveOrderOutPort;

    private final FindOrderOutPort findOrderOutPort;

    private final GenerateOrderNumberOutPort generateOrderNumberOutPort;

    private final FindSequenceNumberOutPort findSequenceNumberOutPort;

    @Override
    public Order saveOrder(final Order order) {

        final String orderNumber = generateOrderNumberOutPort.generate(findSequenceNumberOutPort.find());
        return saveOrderOutPort.save(
                Order.builder()
                        .customer(order.getCustomer())
                        .orderNumber(orderNumber)
                        .created(order.getCreated())
                        .remarks(order.getRemarks())
                        .items(order.getItems())
                        .build());
    }

    @Override
    public Order findOrder(final String orderNumber) {

        return findOrderOutPort.find(orderNumber);
    }

}
