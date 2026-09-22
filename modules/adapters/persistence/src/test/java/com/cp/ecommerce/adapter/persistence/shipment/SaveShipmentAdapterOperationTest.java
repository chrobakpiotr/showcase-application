package com.cp.ecommerce.adapter.persistence.shipment;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaveShipmentAdapterOperationTest {

    private static final String OPERATION_ID = "op-1";
    private static final String SHIPMENT_NUMBER = "SHIP-1";

    @Mock
    private ShipmentEntityRepository shipmentEntityRepository;

    @Mock
    private ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Mock
    private ShipmentOperationEntityRepository operationRepository;

    private SaveShipmentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SaveShipmentAdapter(shipmentEntityRepository, shipmentPersistenceMapper, operationRepository);
    }

    @Test
    void shouldReturnNullWhenOperationDoesNotExist() {

        given(operationRepository.findById(OPERATION_ID)).willReturn(Optional.empty());

        assertThat(adapter.findOperation(OPERATION_ID)).isNull();
    }

    @Test
    void shouldMapPersistedOperationToDomain() {

        final Instant dispatched = Instant.parse("2026-09-22T10:00:00Z");
        given(operationRepository.findById(OPERATION_ID)).willReturn(
                Optional.of(
                        ShipmentOperationEntity.builder()
                                .operationId(OPERATION_ID)
                                .shipmentNumber(SHIPMENT_NUMBER)
                                .expectedStatus(ShipmentStatus.PENDING)
                                .resultStatus(ShipmentStatus.DISPATCHED)
                                .dispatchedDate(dispatched)
                                .estimatedDeliveryDate(dispatched.plusSeconds(432000))
                                .resultVersion(3)
                                .build()));

        final ShipmentOperation result = adapter.findOperation(OPERATION_ID);

        assertThat(result.getOperationId()).isEqualTo(OPERATION_ID);
        assertThat(result.getShipmentNumber()).isEqualTo(SHIPMENT_NUMBER);
        assertThat(result.getExpectedStatus()).isEqualTo(ShipmentStatus.PENDING);
        assertThat(result.getResultStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(result.getDispatchedDate()).isEqualTo(dispatched);
        assertThat(result.getEstimatedDeliveryDate()).isEqualTo(dispatched.plusSeconds(432000));
        assertThat(result.getDeliveredDate()).isNull();
        assertThat(result.getResultVersion()).isEqualTo(3);
    }

    @Test
    void shouldPersistOperationSnapshot() {

        final Instant delivered = Instant.parse("2026-09-22T12:00:00Z");
        final ShipmentOperation operation = ShipmentOperation.builder()
                .operationId("op-2")
                .shipmentNumber(SHIPMENT_NUMBER)
                .expectedStatus(ShipmentStatus.IN_TRANSIT)
                .resultStatus(ShipmentStatus.DELIVERED)
                .deliveredDate(delivered)
                .resultVersion(5)
                .build();

        adapter.saveOperation(operation);

        final ArgumentCaptor<ShipmentOperationEntity> captor = ArgumentCaptor.forClass(ShipmentOperationEntity.class);
        verify(operationRepository).save(captor.capture());

        final ShipmentOperationEntity saved = captor.getValue();
        assertThat(saved.getOperationId()).isEqualTo("op-2");
        assertThat(saved.getShipmentNumber()).isEqualTo(SHIPMENT_NUMBER);
        assertThat(saved.getExpectedStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(saved.getResultStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(saved.getDeliveredDate()).isEqualTo(delivered);
        assertThat(saved.getResultVersion()).isEqualTo(5);
    }
}
