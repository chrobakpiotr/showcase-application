package com.cp.ecommerce.adapter.persistence.shipment.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;
import com.cp.ecommerce.domain.shipment.Shipment;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link Shipment} object into/from entity object.
 */
@Component
public class ShipmentPersistenceMapper implements PersistenceMapper<Shipment, ShipmentEntity> {

    @Override
    public Optional<ShipmentEntity> mapToEntity(final Shipment shipment) {

        return ofNullable(shipment).map(
                domain -> ShipmentEntity.builder()
                        .shipmentNumber(domain.getShipmentNumber())
                        .orderNumber(domain.getOrderNumber())
                        .carrier(domain.getCarrier())
                        .trackingNumber(domain.getTrackingNumber())
                        .status(domain.getStatus())
                        .dispatchedDate(domain.getDispatchedDate())
                        .estimatedDeliveryDate(domain.getEstimatedDeliveryDate())
                        .deliveredDate(domain.getDeliveredDate())
                        .createdDate(domain.getCreatedDate())
                        .build());
    }

    @Override
    public Optional<Shipment> mapToDomainObject(final ShipmentEntity entity) {

        return ofNullable(entity).map(
                shipmentEntity -> Shipment.builder()
                        .shipmentNumber(shipmentEntity.getShipmentNumber())
                        .orderNumber(shipmentEntity.getOrderNumber())
                        .carrier(shipmentEntity.getCarrier())
                        .trackingNumber(shipmentEntity.getTrackingNumber())
                        .status(shipmentEntity.getStatus())
                        .dispatchedDate(shipmentEntity.getDispatchedDate())
                        .estimatedDeliveryDate(shipmentEntity.getEstimatedDeliveryDate())
                        .deliveredDate(shipmentEntity.getDeliveredDate())
                        .createdDate(shipmentEntity.getCreatedDate())
                        .build());
    }

}
