package com.cp.ecommerce.adapter.persistence.shipment.mapper;

import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.persistence.utils.ShipmentEntityBuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ShipmentPersistenceMapper}.
 */
class ShipmentPersistenceMapperTest {

    private final transient ShipmentPersistenceMapper shipmentPersistenceMapper = new ShipmentPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final var shipment = ShipmentBuilder.mockShipment();

        final var result = shipmentPersistenceMapper.mapToEntity(shipment);

        assertTrue(result.isPresent());
        assertEquals(shipment.getShipmentNumber(), result.get().getShipmentNumber());
        assertEquals(shipment.getOrderNumber(), result.get().getOrderNumber());
        assertEquals(shipment.getCarrier(), result.get().getCarrier());
        assertEquals(shipment.getTrackingNumber(), result.get().getTrackingNumber());
        assertEquals(shipment.getStatus(), result.get().getStatus());
        assertEquals(shipment.getCreatedDate(), result.get().getCreatedDate());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();

        final var result = shipmentPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getShipmentNumber(), result.get().getShipmentNumber());
        assertEquals(entity.getOrderNumber(), result.get().getOrderNumber());
        assertEquals(entity.getCarrier(), result.get().getCarrier());
        assertEquals(entity.getTrackingNumber(), result.get().getTrackingNumber());
        assertEquals(entity.getStatus(), result.get().getStatus());
        assertEquals(entity.getDispatchedDate(), result.get().getDispatchedDate());
        assertEquals(entity.getEstimatedDeliveryDate(), result.get().getEstimatedDeliveryDate());
        assertEquals(entity.getDeliveredDate(), result.get().getDeliveredDate());
        assertEquals(entity.getCreatedDate(), result.get().getCreatedDate());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(shipmentPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(shipmentPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
