package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.customer.port.incoming.ManageCustomerInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PlaceOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case for placing order.
 *
 * <p>
 * This use case only guarantees that the order is durably saved (order row + {@code PENDING} outbox row written atomically, per
 * the transactional outbox pattern). Everything else the order placement fans out to - confirmation email, fulfillment
 * notification, export, audit, analytics - is deliberately left to the asynchronous order-placement saga
 * ({@code OrderPlacementSagaOrchestrator}) so that a slow/unavailable downstream dependency can never turn an order that was
 * actually placed into a failed HTTP response.
 */
@Slf4j
@RequiredArgsConstructor
@UseCase
public class PlaceOrderUseCase implements PlaceOrderInPort {

    private final ManageOrderInPort manageOrderInPort;

    private final LogOrderOutPort logOrderOutPort;

    private final ManageCustomerInPort manageCustomerInPort;

    @Override
    public String placeOrder(final Order order) {

        if (!manageCustomerInPort.checkCustomerExists(order.getCustomer().getContact().getEmail())) {

            log.info("Saving order data started...");
            final Order savedOrder = manageOrderInPort.saveOrder(order);
            log.info("Saving order completed.");

            log.info("Order's number: {}", savedOrder.getOrderNumber());
            logOrderOutPort.log(savedOrder);

            return savedOrder.getOrderNumber();
        } else {

            log.info(
                    "Customer with email '{}' already exists, order will not be placed.",
                    order.getCustomer().getContact().getEmail());
            return "";
        }
    }

}
