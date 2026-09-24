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
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
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

    private final EntityManager entityManager;

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
    public void lockShipment(final String shipmentNumber) {

        shipmentEntityRepository.findByShipmentNumberForUpdate(shipmentNumber);
    }

    @Override
    public ShipmentOperation findOperation(final String operationId) {

        return shipmentOperationEntityRepository.findById(operationId).map(SaveShipmentAdapter::toOperation).orElse(null);
    }

    @Override
    @Transactional
    public void saveOperation(final ShipmentOperation operation) {

        insertOperationIfAbsent(operation);

        final ShipmentOperation canonical = shipmentOperationEntityRepository.findById(operation.getOperationId())
                .map(SaveShipmentAdapter::toOperation)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Shipment operation insert-once did not resolve operation id: " + operation.getOperationId()));

        if (!operation.equals(canonical)) {
            throw new ShipmentConflictException(
                    "Shipment operation '" + operation.getOperationId() + "' was reused with a different immutable snapshot");
        }
    }

    private void insertOperationIfAbsent(final ShipmentOperation operation) {

        entityManager.createNativeQuery("""
                insert into test_db.SHIPMENT_OPERATION (
                    OPERATION_ID,
                    SHIPMENT_NUMBER,
                    EXPECTED_STATUS,
                    RESULT_STATUS,
                    DISPATCHED_DATE,
                    ESTIMATED_DELIVERY_DATE,
                    DELIVERED_DATE,
                    RESULT_VERSION
                ) values (
                    :operationId,
                    :shipmentNumber,
                    :expectedStatus,
                    :resultStatus,
                    :dispatchedDate,
                    :estimatedDeliveryDate,
                    :deliveredDate,
                    :resultVersion
                )
                on conflict (OPERATION_ID) do nothing
                """)
                .setParameter("operationId", operation.getOperationId())
                .setParameter("shipmentNumber", operation.getShipmentNumber())
                .setParameter("expectedStatus", operation.getExpectedStatus().name())
                .setParameter("resultStatus", operation.getResultStatus().name())
                .setParameter("dispatchedDate", operation.getDispatchedDate())
                .setParameter("estimatedDeliveryDate", operation.getEstimatedDeliveryDate())
                .setParameter("deliveredDate", operation.getDeliveredDate())
                .setParameter("resultVersion", operation.getResultVersion())
                .executeUpdate();
    }

    private static ShipmentOperation toOperation(final ShipmentOperationEntity entity) {

        return ShipmentOperation.builder()
                .operationId(entity.getOperationId())
                .shipmentNumber(entity.getShipmentNumber())
                .expectedStatus(entity.getExpectedStatus())
                .resultStatus(entity.getResultStatus())
                .dispatchedDate(entity.getDispatchedDate())
                .estimatedDeliveryDate(entity.getEstimatedDeliveryDate())
                .deliveredDate(entity.getDeliveredDate())
                .resultVersion(entity.getResultVersion())
                .build();
    }
}
