package com.cp.ecommerce.adapter.persistence.shipment;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ShipmentBuilder;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ShipmentEntityBuilder;
import com.cp.ecommerce.domain.shipment.PageQuery;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void shouldFindPagedShipments() {
        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(new PageImpl<>(List.of(entity), PageRequest.of(1, 10), 25)).when(shipmentEntityRepository)
                .findAllByOrderByCreatedDateDesc(PageRequest.of(1, 10));
        doReturn(Optional.of(ShipmentBuilder.mockShipment())).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));
        final var result = findShipmentsAdapter.findAll(new PageQuery(1, 10));
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldFindPagedShipmentsByOrderNumber() {
        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 5), 1)).when(shipmentEntityRepository)
                .findByOrderNumberOrderByCreatedDateDesc(ShipmentBuilder.TEST_ORDER_NUMBER, PageRequest.of(0, 5));
        doReturn(Optional.of(ShipmentBuilder.mockShipment())).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));
        assertThat(findShipmentsAdapter.findByOrderNumber(ShipmentBuilder.TEST_ORDER_NUMBER, new PageQuery(0, 5)).content())
                .hasSize(1);
    }

    @Test
    void shouldFindPagedShipmentsByStatus() {
        final var entity = ShipmentEntityBuilder.mockShipmentEntity();
        doReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 5), 1)).when(shipmentEntityRepository)
                .findByStatusOrderByCreatedDateDesc(ShipmentStatus.PENDING, PageRequest.of(0, 5));
        doReturn(Optional.of(ShipmentBuilder.mockShipment())).when(shipmentPersistenceMapper).mapToDomainObject(eq(entity));
        assertThat(findShipmentsAdapter.findByStatus(ShipmentStatus.PENDING, new PageQuery(0, 5)).content()).hasSize(1);
    }

}
