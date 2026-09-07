package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentsOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindShipmentsOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindShipmentsAdapter implements FindShipmentsOutPort {

    private final ShipmentEntityRepository shipmentEntityRepository;

    private final ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Override
    public List<Shipment> findAll() {

        return shipmentEntityRepository.findAllByOrderByCreatedDateDesc().stream().map(this::mapToDomainObjectOrThrow).toList();
    }

    @Override
    public List<Shipment> findByOrderNumber(final String orderNumber) {

        return shipmentEntityRepository.findByOrderNumberOrderByCreatedDateDesc(orderNumber)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    @Override
    public List<Shipment> findByStatus(final ShipmentStatus status) {

        return shipmentEntityRepository.findByStatusOrderByCreatedDateDesc(status)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    private Shipment mapToDomainObjectOrThrow(final ShipmentEntity entity) {

        return shipmentPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map shipment entity to domain object for shipment number: "
                                        + entity.getShipmentNumber()));
    }

}
