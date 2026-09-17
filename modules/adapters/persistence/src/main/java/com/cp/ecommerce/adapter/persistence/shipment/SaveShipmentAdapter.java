package com.cp.ecommerce.adapter.persistence.shipment;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveShipmentOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveShipmentAdapter implements SaveShipmentOutPort {

    private final ShipmentEntityRepository shipmentEntityRepository;

    private final ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Override
    public Shipment save(final Shipment shipment) {

        final ShipmentEntity entityToSave = shipmentPersistenceMapper.mapToEntity(shipment)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map shipment domain object to entity for shipment number: "
                                        + shipment.getShipmentNumber()));
        final ShipmentEntity saved = shipmentEntityRepository.save(entityToSave);
        return shipmentPersistenceMapper.mapToDomainObject(saved)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map shipment entity to domain object for shipment number: "
                                        + shipment.getShipmentNumber()));
    }

}
