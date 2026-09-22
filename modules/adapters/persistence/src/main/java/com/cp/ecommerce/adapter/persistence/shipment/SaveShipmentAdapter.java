package com.cp.ecommerce.adapter.persistence.shipment;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;
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

    private final ShipmentOperationEntityRepository shipmentOperationEntityRepository;

    @Override
    public Shipment save(final Shipment shipment) {

        final ShipmentEntity entityToSave = shipmentPersistenceMapper.mapToEntity(shipment)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map shipment domain object to entity for shipment number: "
                                        + shipment.getShipmentNumber()));
        final ShipmentEntity saved = shipmentEntityRepository.saveAndFlush(entityToSave);
        return shipmentPersistenceMapper.mapToDomainObject(saved)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map shipment entity to domain object for shipment number: "
                                        + shipment.getShipmentNumber()));
    }

    @Override
    public ShipmentOperation findOperation(final String operationId) {

        return shipmentOperationEntityRepository.findById(operationId)
                .map(
                        entity -> ShipmentOperation.builder()
                                .operationId(entity.getOperationId())
                                .shipmentNumber(entity.getShipmentNumber())
                                .expectedStatus(entity.getExpectedStatus())
                                .resultStatus(entity.getResultStatus())
                                .dispatchedDate(entity.getDispatchedDate())
                                .estimatedDeliveryDate(entity.getEstimatedDeliveryDate())
                                .deliveredDate(entity.getDeliveredDate())
                                .resultVersion(entity.getResultVersion())
                                .build())
                .orElse(null);
    }

    @Override
    public void saveOperation(final ShipmentOperation operation) {

        shipmentOperationEntityRepository.save(
                ShipmentOperationEntity.builder()
                        .operationId(operation.getOperationId())
                        .shipmentNumber(operation.getShipmentNumber())
                        .expectedStatus(operation.getExpectedStatus())
                        .resultStatus(operation.getResultStatus())
                        .dispatchedDate(operation.getDispatchedDate())
                        .estimatedDeliveryDate(operation.getEstimatedDeliveryDate())
                        .deliveredDate(operation.getDeliveredDate())
                        .resultVersion(operation.getResultVersion())
                        .build());
    }

}
