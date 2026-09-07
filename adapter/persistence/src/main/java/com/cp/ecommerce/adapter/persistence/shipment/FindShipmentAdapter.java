package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindShipmentOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindShipmentAdapter implements FindShipmentOutPort {

    private final ShipmentEntityRepository shipmentEntityRepository;

    private final ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Override
    public Shipment findByShipmentNumber(final String shipmentNumber) {

        return Optional.ofNullable(shipmentEntityRepository.findByShipmentNumber(shipmentNumber))
                .map(
                        entity -> shipmentPersistenceMapper.mapToDomainObject(entity)
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "Failed to map shipment entity to domain object for shipment number: "
                                                        + shipmentNumber)))
                .orElse(null);
    }

    @Override
    public Shipment findByOrderNumber(final String orderNumber) {

        return Optional.ofNullable(shipmentEntityRepository.findByOrderNumber(orderNumber))
                .map(
                        entity -> shipmentPersistenceMapper.mapToDomainObject(entity)
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "Failed to map shipment entity to domain object for order number: "
                                                        + orderNumber)))
                .orElse(null);
    }

}
