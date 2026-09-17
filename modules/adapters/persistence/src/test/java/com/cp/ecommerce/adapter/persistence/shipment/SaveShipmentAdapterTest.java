package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ShipmentEntityBuilder;
import com.cp.ecommerce.domain.shipment.Shipment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link SaveShipmentAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveShipmentAdapterTest {

    @InjectMocks
    private transient SaveShipmentAdapter saveShipmentAdapter;

    @Mock
    private transient ShipmentEntityRepository shipmentEntityRepository;

    @Mock
    private transient ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedShipment() {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        final ShipmentEntity mappedEntity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(Optional.of(mappedEntity)).when(shipmentPersistenceMapper).mapToEntity(eq(shipment));
        doReturn(mappedEntity).when(shipmentEntityRepository).save(mappedEntity);
        doReturn(Optional.of(shipment)).when(shipmentPersistenceMapper).mapToDomainObject(mappedEntity);

        final Shipment result = saveShipmentAdapter.save(shipment);

        assertEquals(shipment, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        doReturn(Optional.empty()).when(shipmentPersistenceMapper).mapToEntity(eq(shipment));

        assertThrows(IllegalStateException.class, () -> saveShipmentAdapter.save(shipment));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Shipment shipment = ShipmentBuilder.mockShipment();
        final ShipmentEntity mappedEntity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(Optional.of(mappedEntity)).when(shipmentPersistenceMapper).mapToEntity(eq(shipment));
        doReturn(mappedEntity).when(shipmentEntityRepository).save(mappedEntity);
        doReturn(Optional.empty()).when(shipmentPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveShipmentAdapter.save(shipment));
    }

}
