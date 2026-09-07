package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ShipmentEntityBuilder;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindShipmentsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindShipmentsAdapterTest {

    @InjectMocks
    private transient FindShipmentsAdapter findShipmentsAdapter;

    @Mock
    private transient ShipmentEntityRepository shipmentEntityRepository;

    @Mock
    private transient ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Test
    void shouldFindAllShipments() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(List.of(entity)).when(shipmentEntityRepository).findAllByOrderByCreatedDateDesc();
        doReturn(Optional.of(ShipmentBuilder.mockShipment())).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        assertThat(findShipmentsAdapter.findAll()).hasSize(1);
    }

    @Test
    void shouldFindShipmentsByOrderNumber() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(List.of(entity)).when(shipmentEntityRepository)
                .findByOrderNumberOrderByCreatedDateDesc(ShipmentBuilder.TEST_ORDER_NUMBER);
        doReturn(Optional.of(ShipmentBuilder.mockShipment())).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        assertThat(findShipmentsAdapter.findByOrderNumber(ShipmentBuilder.TEST_ORDER_NUMBER)).hasSize(1);
    }

    @Test
    void shouldFindShipmentsByStatus() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(List.of(entity)).when(shipmentEntityRepository).findByStatusOrderByCreatedDateDesc(ShipmentStatus.PENDING);
        doReturn(Optional.of(ShipmentBuilder.mockShipment())).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        assertThat(findShipmentsAdapter.findByStatus(ShipmentStatus.PENDING)).hasSize(1);
    }

    @Test
    void shouldThrowWhenFindAllCannotMapShipment() {

        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(List.of(entity)).when(shipmentEntityRepository).findAllByOrderByCreatedDateDesc();
        doReturn(Optional.empty()).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findShipmentsAdapter.findAll()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ShipmentBuilder.TEST_SHIPMENT_NUMBER);
    }

}
