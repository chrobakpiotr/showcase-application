package com.cp.ecommerce.domain.shipment.usecase;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.GetShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.ListShipmentsInPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentsOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateShipmentNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateTrackingNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating, advancing and reading shipments.
 */
@UseCase
@RequiredArgsConstructor
public class ManageShipmentUseCase
        implements CreateShipmentInPort, AdvanceShipmentStatusInPort, GetShipmentInPort, ListShipmentsInPort {

    private static final Duration DEFAULT_ESTIMATED_DELIVERY_LEAD_TIME = Duration.ofDays(5);

    private final SaveShipmentOutPort saveShipmentOutPort;

    private final FindShipmentOutPort findShipmentOutPort;

    private final FindShipmentsOutPort findShipmentsOutPort;

    private final GenerateShipmentNumberOutPort generateShipmentNumberOutPort;

    private final GenerateTrackingNumberOutPort generateTrackingNumberOutPort;

    @Override
    public Shipment createShipment(final String orderNumber, final String carrier) {

        if (findShipmentOutPort.findByOrderNumber(orderNumber) != null) {

            throw new ShipmentConflictException("Shipment already exists for order: " + orderNumber);
        }
        final Shipment shipment = Shipment.builder()
                .shipmentNumber(generateShipmentNumberOutPort.generate())
                .orderNumber(orderNumber)
                .carrier(carrier)
                .trackingNumber(generateTrackingNumberOutPort.generate(carrier))
                .status(ShipmentStatus.PENDING)
                .createdDate(new Date())
                .build();
        shipment.assertValidationsEmpty();
        return saveShipmentOutPort.save(shipment);
    }

    @Override
    public Shipment advanceShipmentStatus(final String shipmentNumber) {

        final Shipment existing = findShipmentOutPort.findByShipmentNumber(shipmentNumber);
        if (existing == null) {

            return null;
        }
        final ShipmentStatus nextStatus = nextStatus(existing);
        final Date now = new Date();
        final Date dispatchedDate = nextStatus == ShipmentStatus.DISPATCHED ? now : existing.getDispatchedDate();
        final Date estimatedDeliveryDate = nextStatus == ShipmentStatus.DISPATCHED
                ? Date.from(now.toInstant().plus(DEFAULT_ESTIMATED_DELIVERY_LEAD_TIME))
                : existing.getEstimatedDeliveryDate();
        final Date deliveredDate = nextStatus == ShipmentStatus.DELIVERED ? now : existing.getDeliveredDate();
        final Shipment advanced = Shipment.builder()
                .shipmentNumber(existing.getShipmentNumber())
                .orderNumber(existing.getOrderNumber())
                .carrier(existing.getCarrier())
                .trackingNumber(existing.getTrackingNumber())
                .status(nextStatus)
                .dispatchedDate(dispatchedDate)
                .estimatedDeliveryDate(estimatedDeliveryDate)
                .deliveredDate(deliveredDate)
                .createdDate(existing.getCreatedDate())
                .build();
        advanced.assertValidationsEmpty();
        return saveShipmentOutPort.save(advanced);
    }

    @Override
    public Shipment getShipment(final String shipmentNumber) {

        return findShipmentOutPort.findByShipmentNumber(shipmentNumber);
    }

    @Override
    public List<Shipment> listShipments() {

        return findShipmentsOutPort.findAll();
    }

    @Override
    public List<Shipment> listShipmentsForOrder(final String orderNumber) {

        return findShipmentsOutPort.findByOrderNumber(orderNumber);
    }

    @Override
    public List<Shipment> listShipmentsByStatus(final ShipmentStatus status) {

        return findShipmentsOutPort.findByStatus(status);
    }

    private ShipmentStatus nextStatus(final Shipment existing) {

        return switch (existing.getStatus()) {
        case PENDING -> ShipmentStatus.DISPATCHED;
        case DISPATCHED -> ShipmentStatus.IN_TRANSIT;
        case IN_TRANSIT -> ShipmentStatus.DELIVERED;
        case DELIVERED ->
            throw new ShipmentConflictException("Shipment '" + existing.getShipmentNumber() + "' is already DELIVERED");
        };
    }

}
