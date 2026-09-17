package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ShipmentEntityBuilder;
import com.cp.ecommerce.domain.shipment.Shipment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindShipmentAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindShipmentAdapterTest {

    @InjectMocks
    private transient FindShipmentAdapter findShipmentAdapter;

    @Mock
    private transient ShipmentEntityRepository shipmentEntityRepository;

    @Mock
    private transient ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Test
    void shouldReturnMappedShipmentWhenFoundByShipmentNumber() {

        final Shipment expected = ShipmentBuilder.mockShipment();
        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(entity).when(shipmentEntityRepository).findByShipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER);
        doReturn(Optional.of(expected)).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        final Shipment result = findShipmentAdapter.findByShipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER);

        assertEquals(expected, result);
    }

    @Test
    void shouldReturnMappedShipmentWhenFoundByOrderNumber() {

        final Shipment expected = ShipmentBuilder.mockShipment();
        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(entity).when(shipmentEntityRepository).findByOrderNumber(ShipmentBuilder.TEST_ORDER_NUMBER);
        doReturn(Optional.of(expected)).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        final Shipment result = findShipmentAdapter.findByOrderNumber(ShipmentBuilder.TEST_ORDER_NUMBER);

        assertEquals(expected, result);
    }

    @Test
    void shouldReturnNullWhenShipmentNotFound() {

        doReturn(null).when(shipmentEntityRepository).findByShipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER);

        assertNull(findShipmentAdapter.findByShipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER));
    }

    @Test
    void shouldThrowWhenMappedShipmentIsMissing() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(entity).when(shipmentEntityRepository).findByShipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER);
        doReturn(Optional.empty()).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findShipmentAdapter.findByShipmentNumber(ShipmentBuilder.TEST_SHIPMENT_NUMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ShipmentBuilder.TEST_SHIPMENT_NUMBER);
    }

    @Test
    void shouldThrowWhenMappedShipmentIsMissingForOrderNumber() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(entity).when(shipmentEntityRepository).findByOrderNumber(ShipmentBuilder.TEST_ORDER_NUMBER);
        doReturn(Optional.empty()).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findShipmentAdapter.findByOrderNumber(ShipmentBuilder.TEST_ORDER_NUMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ShipmentBuilder.TEST_ORDER_NUMBER);
    }

}
