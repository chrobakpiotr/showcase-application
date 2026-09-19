package com.cp.ecommerce.application.shipment;

import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShipmentService implements ShipmentWorkflow {

    private final CreateShipmentInPort createShipmentInPort;

    private final AdvanceShipmentStatusInPort advanceShipmentStatusInPort;

    private final ManageOrderUseCase manageOrderUseCase;

    private final SendNotificationInPort sendNotificationInPort;

    @Override
    public Shipment createShipment(final String orderNumber, final String carrier) {

        final Order order = requireOrder(orderNumber);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only CONFIRMED orders can be shipped");
        }
        return createShipmentInPort.createShipment(orderNumber, carrier);
    }

    @Override
    public Shipment advanceShipment(final String shipmentNumber) {

        final Shipment advanced = advanceShipmentStatusInPort.advanceShipmentStatus(shipmentNumber);
        if (advanced == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        }
        if (advanced.getStatus() == ShipmentStatus.DISPATCHED) {
            notify(advanced, NotificationType.SHIPMENT_DISPATCHED, "dispatched");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }
}
