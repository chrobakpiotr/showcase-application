package com.cp.ecommerce.domain.shipment.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.shipment.PageQuery;
import com.cp.ecommerce.domain.shipment.PagedResult;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentsOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateShipmentNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateTrackingNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageShipmentUseCaseMutationWave2Test {

    private static final String SHIPMENT = "SHIP-1";
    private static final String ORDER = "ORDER-1";
    private static final String TRACKING = "TRACK-1";
    private static final String CARRIER = "DHL";
    private static final Instant CREATED = Instant.parse("2026-09-19T10:00:00Z");
    private static final Instant DISPATCHED = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant ESTIMATED = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private SaveShipmentOutPort saveShipmentOutPort;
    @Mock
    private FindShipmentOutPort findShipmentOutPort;
    @Mock
    private FindShipmentsOutPort findShipmentsOutPort;
    @Mock
    private GenerateShipmentNumberOutPort generateShipmentNumberOutPort;
    @Mock
    private GenerateTrackingNumberOutPort generateTrackingNumberOutPort;

    private ManageShipmentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageShipmentUseCase(
                saveShipmentOutPort,
                findShipmentOutPort,
                findShipmentsOutPort,
                generateShipmentNumberOutPort,
                generateTrackingNumberOutPort);
    }

    @Test
    void shouldDelegateAllPagedReadsWithoutReplacingReturnedPage() {
        final PageQuery query = new PageQuery(0, 20);
        final PagedResult<Shipment> page = new PagedResult<>(
                List.of(shipment(ShipmentStatus.PENDING, null, null, null, null)),
                0,
                20,
                1,
                1);
        given(findShipmentsOutPort.findAll(query)).willReturn(page);
        given(findShipmentsOutPort.findByOrderNumber(ORDER, query)).willReturn(page);
        given(findShipmentsOutPort.findByStatus(ShipmentStatus.PENDING, query)).willReturn(page);

        assertThat(useCase.listShipments(query)).isSameAs(page);
        assertThat(useCase.listShipmentsForOrder(ORDER, query)).isSameAs(page);
        assertThat(useCase.listShipmentsByStatus(ShipmentStatus.PENDING, query)).isSameAs(page);
    }

    @Test
    void shouldReturnNullForMissingOperationAwareShipment() {
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(null);

        assertThat(useCase.advanceShipmentStatus(SHIPMENT, "op-1", ShipmentStatus.PENDING)).isNull();
        verify(saveShipmentOutPort, never()).save(any());
    }

    @Test
    void shouldReplaySameOperationWithoutSavingAgain() {
        final Shipment existing = shipment(ShipmentStatus.DISPATCHED, "op-later", DISPATCHED, ESTIMATED, null);
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(existing);
        given(saveShipmentOutPort.findOperation("op-1")).willReturn(
                ShipmentOperation.builder()
                        .operationId("op-1")
                        .shipmentNumber(SHIPMENT)
                        .expectedStatus(ShipmentStatus.PENDING)
                        .resultStatus(ShipmentStatus.DISPATCHED)
                        .dispatchedDate(DISPATCHED)
                        .estimatedDeliveryDate(ESTIMATED)
                        .resultVersion(7)
                        .build());

        final Shipment replay = useCase.advanceShipmentStatus(SHIPMENT, "op-1", ShipmentStatus.PENDING);

        assertThat(replay.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(replay.getLastOperationId()).isEqualTo("op-1");
        assertThat(replay.getDispatchedDate()).isEqualTo(DISPATCHED);
        assertThat(replay.getEstimatedDeliveryDate()).isEqualTo(ESTIMATED);
        assertThat(replay.getVersion()).isEqualTo(7);
        verify(saveShipmentOutPort, never()).save(any());
        verify(saveShipmentOutPort, never()).saveOperation(any());
        final InOrder lockOrder = inOrder(saveShipmentOutPort, findShipmentOutPort);
        lockOrder.verify(saveShipmentOutPort).lockShipment(SHIPMENT);
        lockOrder.verify(findShipmentOutPort).findByShipmentNumber(SHIPMENT);
        lockOrder.verify(saveShipmentOutPort).findOperation("op-1");
    }

    @Test
    void shouldRejectMismatchedExpectedStatusBeforeSaving() {
        final Shipment existing = shipment(ShipmentStatus.DISPATCHED, "old", DISPATCHED, ESTIMATED, null);
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(existing);

        assertThatThrownBy(() -> useCase.advanceShipmentStatus(SHIPMENT, "op-2", ShipmentStatus.PENDING))
                .isInstanceOf(ShipmentConflictException.class)
                .hasMessageContaining("expected PENDING");
        verify(saveShipmentOutPort, never()).save(any());
    }

    @Test
    void shouldRecordStableOperationIdentityAndFiveDayEstimateOnDispatch() {
        final Shipment existing = shipment(ShipmentStatus.PENDING, null, null, null, null);
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(existing);
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = useCase.advanceShipmentStatus(SHIPMENT, "op-dispatch", ShipmentStatus.PENDING);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(result.getLastOperationId()).isEqualTo("op-dispatch");
        assertThat(result.getDispatchedDate()).isNotNull();
        assertThat(Duration.between(result.getDispatchedDate(), result.getEstimatedDeliveryDate()))
                .isEqualTo(Duration.ofDays(5));
        assertThat(result.getDeliveredDate()).isNull();
        assertThat(result.getVersion()).isEqualTo(7);
        assertThat(result.getOrderNumber()).isEqualTo(ORDER);
        assertThat(result.getTrackingNumber()).isEqualTo(TRACKING);
    }

    @Test
    void shouldPreserveDispatchDatesWhenMovingToInTransit() {
        final Shipment existing = shipment(ShipmentStatus.DISPATCHED, "op-old", DISPATCHED, ESTIMATED, null);
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(existing);
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = useCase.advanceShipmentStatus(SHIPMENT, "op-transit", ShipmentStatus.DISPATCHED);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(result.getDispatchedDate()).isEqualTo(DISPATCHED);
        assertThat(result.getEstimatedDeliveryDate()).isEqualTo(ESTIMATED);
        assertThat(result.getDeliveredDate()).isNull();
        assertThat(result.getLastOperationId()).isEqualTo("op-transit");
    }

    @Test
    void shouldSetDeliveredDateWithoutChangingPriorLifecycleDates() {
        final Shipment existing = shipment(ShipmentStatus.IN_TRANSIT, "op-old", DISPATCHED, ESTIMATED, null);
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(existing);
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Shipment result = useCase.advanceShipmentStatus(SHIPMENT, "op-deliver", ShipmentStatus.IN_TRANSIT);

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getDispatchedDate()).isEqualTo(DISPATCHED);
        assertThat(result.getEstimatedDeliveryDate()).isEqualTo(ESTIMATED);
        assertThat(result.getDeliveredDate()).isNotNull();
        assertThat(result.getLastOperationId()).isEqualTo("op-deliver");
    }

    @Test
    void shouldValidateNewShipmentBeforeSave() {
        given(findShipmentOutPort.findByOrderNumber(ORDER)).willReturn(null);
        given(generateShipmentNumberOutPort.generate()).willReturn(SHIPMENT);
        given(generateTrackingNumberOutPort.generate(" ")).willReturn(TRACKING);

        assertThatThrownBy(() -> useCase.createShipment(ORDER, " ")).isInstanceOf(DomainObjectValidationException.class);
        verify(saveShipmentOutPort, never()).save(any());
    }

    private static Shipment shipment(
            final ShipmentStatus status,
            final String lastOperationId,
            final Instant dispatched,
            final Instant estimated,
            final Instant delivered) {
        return Shipment.builder()
                .shipmentNumber(SHIPMENT)
                .orderNumber(ORDER)
                .carrier(CARRIER)
                .trackingNumber(TRACKING)
                .status(status)
                .dispatchedDate(dispatched)
                .estimatedDeliveryDate(estimated)
                .deliveredDate(delivered)
                .createdDate(CREATED)
                .version(7)
                .lastOperationId(lastOperationId)
                .build();
    }
}
