package com.cp.ecommerce.adapter.persistence.shipment;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.mapper.ShipmentPersistenceMapper;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SaveShipmentAdapterOperationTest {

    private static final String OPERATION_ID = "op-1";
    private static final String SHIPMENT_NUMBER = "SHIP-1";
    private static final Instant DISPATCHED = Instant.parse("2026-09-22T10:00:00Z");
    private static final Instant ESTIMATED = DISPATCHED.plusSeconds(432000);

    @Mock
    private ShipmentEntityRepository shipmentEntityRepository;

    @Mock
    private ShipmentPersistenceMapper shipmentPersistenceMapper;

    @Mock
    private ShipmentOperationEntityRepository operationRepository;

    @Mock
    private EntityManager entityManager;

    @Mock(answer = Answers.RETURNS_SELF)
    private Query query;

    private SaveShipmentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SaveShipmentAdapter(
                shipmentEntityRepository,
                shipmentPersistenceMapper,
                operationRepository,
                entityManager);
    }

    @Test
    void shouldReturnNullWhenOperationDoesNotExist() {

        given(operationRepository.findById(OPERATION_ID)).willReturn(Optional.empty());

        assertThat(adapter.findOperation(OPERATION_ID)).isNull();
    }

    @Test
    void shouldMapPersistedOperationToDomain() {

        given(operationRepository.findById(OPERATION_ID)).willReturn(Optional.of(entity(SHIPMENT_NUMBER, 3)));

        assertThat(adapter.findOperation(OPERATION_ID)).isEqualTo(operation(SHIPMENT_NUMBER, 3));
    }

    @Test
    void shouldInsertOperationOnceAndAcceptCanonicalSnapshot() {

        final ShipmentOperation candidate = operation(SHIPMENT_NUMBER, 3);
        prepareNativeInsert();
        given(operationRepository.findById(OPERATION_ID)).willReturn(Optional.of(entity(SHIPMENT_NUMBER, 3)));

        adapter.saveOperation(candidate);

        verify(entityManager).createNativeQuery(anyString());
        verify(query).executeUpdate();
        verify(operationRepository, never()).save(any());
    }

    @Test
    void shouldRejectCanonicalSnapshotOwnedByAnotherShipment() {

        final ShipmentOperation candidate = operation(SHIPMENT_NUMBER, 3);
        prepareNativeInsert();
        given(operationRepository.findById(OPERATION_ID)).willReturn(Optional.of(entity("SHIP-OTHER", 3)));

        assertThatThrownBy(() -> adapter.saveOperation(candidate)).isInstanceOf(ShipmentConflictException.class)
                .hasMessageContaining(OPERATION_ID);

        verify(operationRepository, never()).save(any());
    }

    @Test
    void shouldFailClosedWhenInsertOnceCannotResolveCanonicalOperation() {

        prepareNativeInsert();
        given(operationRepository.findById(OPERATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.saveOperation(operation(SHIPMENT_NUMBER, 3))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OPERATION_ID);
    }

    private void prepareNativeInsert() {
        given(entityManager.createNativeQuery(anyString())).willReturn(query);
        given(query.executeUpdate()).willReturn(1);
    }

    private static ShipmentOperation operation(final String shipmentNumber, final long resultVersion) {
        return ShipmentOperation.builder()
                .operationId(OPERATION_ID)
                .shipmentNumber(shipmentNumber)
                .expectedStatus(ShipmentStatus.PENDING)
                .resultStatus(ShipmentStatus.DISPATCHED)
                .dispatchedDate(DISPATCHED)
                .estimatedDeliveryDate(ESTIMATED)
                .resultVersion(resultVersion)
                .build();
    }

    private static ShipmentOperationEntity entity(final String shipmentNumber, final long resultVersion) {
        return ShipmentOperationEntity.builder()
                .operationId(OPERATION_ID)
                .shipmentNumber(shipmentNumber)
                .expectedStatus(ShipmentStatus.PENDING)
                .resultStatus(ShipmentStatus.DISPATCHED)
                .dispatchedDate(DISPATCHED)
                .estimatedDeliveryDate(ESTIMATED)
                .resultVersion(resultVersion)
                .build();
    }
}
