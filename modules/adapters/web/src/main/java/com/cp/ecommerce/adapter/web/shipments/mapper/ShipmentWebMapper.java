package com.cp.ecommerce.adapter.web.shipments.mapper;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.shipments.resource.CreateShipmentResource;
import com.cp.ecommerce.adapter.web.shipments.resource.ShipmentResource;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping {@link Shipment} objects to and from web resources.
 */
@Component
public class ShipmentWebMapper
        implements WebRequestMapper<Shipment, CreateShipmentResource>, WebResponseMapper<Shipment, ShipmentResource> {

    @Override
    public Optional<Shipment> mapToDomainObject(final CreateShipmentResource resource) {

        return Optional.ofNullable(resource)
                .map(
                        request -> Shipment.builder()
                                .orderNumber(request.orderNumber())
                                .carrier(request.carrier())
                                .trackingNumber("PENDING")
                                .status(ShipmentStatus.PENDING)
                                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                                .build());
    }

    @Override
    public Optional<ShipmentResource> mapToResource(final Shipment shipment) {

        return Optional.ofNullable(shipment)
                .map(
                        domain -> ShipmentResource.builder()
                                .shipmentNumber(domain.getShipmentNumber())
                                .orderNumber(domain.getOrderNumber())
                                .carrier(domain.getCarrier())
                                .trackingNumber(domain.getTrackingNumber())
                                .status(domain.getStatus().name())
                                .dispatchedDate(domain.getDispatchedDate())
                                .estimatedDeliveryDate(domain.getEstimatedDeliveryDate())
                                .deliveredDate(domain.getDeliveredDate())
                                .createdDate(domain.getCreatedDate())
                                .build());
    }

}
