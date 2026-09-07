package com.cp.ecommerce.adapter.web.shipments.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.web.shipments.resource.CreateShipmentResource;
import com.cp.ecommerce.adapter.web.shipments.resource.ShipmentResource;
import com.cp.ecommerce.domain.shipment.Shipment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the shipment mapper behavior.
 */
class ShipmentWebMapperTest {

    private final transient ShipmentWebMapper shipmentWebMapper = new ShipmentWebMapper();

    @Test
    void shouldReturnEmptyIfNullWhileMapToDomainObject() {

        final Optional<Shipment> result = shipmentWebMapper.mapToDomainObject(null);

        assertFalse(result.isPresent());
    }

    @Test
    void shouldMapCreateShipmentResourceToDomainObject() {

        final CreateShipmentResource resource = CreateShipmentResource.builder()
                .orderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)
                .carrier(ShipmentBuilder.TEST_CARRIER)
                .build();

        final Optional<Shipment> result = shipmentWebMapper.mapToDomainObject(resource);

        assertTrue(result.isPresent());
        assertThat(result.get().getOrderNumber()).isEqualTo(ShipmentBuilder.TEST_ORDER_NUMBER);
        assertThat(result.get().getCarrier()).isEqualTo(ShipmentBuilder.TEST_CARRIER);
        assertThat(result.get().getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void shouldReturnEmptyIfNullWhileMapToResource() {

        final Optional<ShipmentResource> resource = shipmentWebMapper.mapToResource(null);

        assertFalse(resource.isPresent());
    }

    @Test
    void shouldMapShipmentToResource() {

        final Shipment shipment = ShipmentBuilder.mockShipment();

        final Optional<ShipmentResource> result = shipmentWebMapper.mapToResource(shipment);

        assertTrue(result.isPresent());
        assertThat(result.get().shipmentNumber()).isEqualTo(ShipmentBuilder.TEST_SHIPMENT_NUMBER);
        assertThat(result.get().trackingNumber()).isEqualTo(ShipmentBuilder.TEST_TRACKING_NUMBER);
        assertThat(result.get().status()).isEqualTo(ShipmentBuilder.TEST_STATUS.name());
    }

}
