package com.cp.ecommerce.application.shipment;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.ListShipmentsInPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShipmentService implements ShipmentWorkflow {

    private final CreateShipmentInPort createShipmentInPort;

    private final AdvanceShipmentStatusInPort advanceShipmentStatusInPort;

    private final ManageOrderUseCase manageOrderUseCase;

    private final GetPaymentInPort getPaymentInPort;

    private final ListShipmentsInPort listShipmentsInPort;

    private final ManageStockInPort manageStockInPort;

    private final SendNotificationInPort sendNotificationInPort;

    @Override
    public Shipment createShipment(final String orderNumber, final String carrier) {

        final Order order = requireOrder(orderNumber);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApplicationConflictException("Only CONFIRMED orders can be shipped");
        }
        if (getPaymentInPort.getPayment(orderNumber).getStatus() != PaymentStatus.CAPTURED) {
            throw new ApplicationConflictException("Only CAPTURED orders can be shipped");
        }
        if (!listShipmentsInPort.listShipmentsForOrder(orderNumber).isEmpty()) {
            throw new ApplicationConflictException("Order already has a shipment");
        }
        return createShipmentInPort.createShipment(orderNumber, carrier);
    }

    @Override
    public Shipment advanceShipment(final String shipmentNumber) {

        final Shipment advanced = advanceShipmentStatusInPort.advanceShipmentStatus(shipmentNumber);
        if (advanced == null) {
            throw new ApplicationNotFoundException("Shipment not found");
        }
        if (advanced.getStatus() == ShipmentStatus.DISPATCHED) {
            final Order order = requireOrder(advanced.getOrderNumber());
            final String reservationId = order.getStockReservationId() == null
                    ? order.getOrderNumber()
                    : order.getStockReservationId();
            order.getItems().forEach(item -> manageStockInPort.fulfillStock(reservationId, item.getSku()));
            notify(advanced, NotificationType.SHIPMENT_DISPATCHED, "dispatched");
        }
        if (advanced.getStatus() == ShipmentStatus.DELIVERED) {
            notify(advanced, NotificationType.SHIPMENT_DELIVERED, "delivered");
        }
        return advanced;
    }

    @Override
    @Transactional
    public Shipment advanceShipment(
            final String shipmentNumber,
            final String operationId,
            final ShipmentStatus expectedStatus) {

        if (expectedStatus == ShipmentStatus.PENDING) {
            final Shipment current = advanceShipmentStatusInPort
                    .advanceShipmentStatus(shipmentNumber, operationId, expectedStatus);
            if (current == null) {
                throw new ApplicationNotFoundException("Shipment not found");
            }
            final Order order = requireOrder(current.getOrderNumber());
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                throw new ApplicationConflictException("Only CONFIRMED orders can be dispatched");
            }
            if (getPaymentInPort.getPayment(order.getOrderNumber()).getStatus() != PaymentStatus.CAPTURED) {
                throw new ApplicationConflictException("Only CAPTURED orders can be dispatched");
            }
            final String reservationId = order.getStockReservationId() == null
                    ? order.getOrderNumber()
                    : order.getStockReservationId();
            order.getItems().forEach(item -> manageStockInPort.fulfillStock(reservationId, item.getSku()));
            notify(current, NotificationType.SHIPMENT_DISPATCHED, "dispatched");
            return current;
        }

        final Shipment advanced = advanceShipmentStatusInPort
                .advanceShipmentStatus(shipmentNumber, operationId, expectedStatus);
        if (advanced == null) {
            throw new ApplicationNotFoundException("Shipment not found");
        }
        if (advanced.getStatus() == ShipmentStatus.DELIVERED) {
            notify(advanced, NotificationType.SHIPMENT_DELIVERED, "delivered");
        }
        return advanced;
    }

    private void notify(final Shipment shipment, final NotificationType type, final String state) {

        final Order order = requireOrder(shipment.getOrderNumber());
        final String message = type == NotificationType.SHIPMENT_DISPATCHED
                ? "Your shipment " + shipment.getShipmentNumber() + " was dispatched. Tracking number: "
                        + shipment.getTrackingNumber() + "."
                : "Your shipment " + shipment.getShipmentNumber() + " was " + state + ".";
        sendNotificationInPort.sendNotification(
                order.getCustomer().getContact().getEmail(),
                type,
                "Shipment " + shipment.getShipmentNumber() + " " + state,
                message);
    }

    private Order requireOrder(final String orderNumber) {

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (order == null) {
            throw new ApplicationNotFoundException("Order not found");
        }
        return order;
    }
}
