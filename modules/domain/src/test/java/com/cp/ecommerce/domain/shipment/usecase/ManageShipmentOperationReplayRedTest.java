package com.cp.ecommerce.domain.shipment.usecase;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.FindShipmentsOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateShipmentNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.GenerateTrackingNumberOutPort;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ManageShipmentOperationReplayRedTest {

    private static final String SHIPMENT = "SHIP-1";
    private static final String ORDER = "ORDER-1";
    private static final Instant CREATED = Instant.parse("2026-09-22T10:00:00Z");

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
    void sameOperationIdWithDifferentExpectedStatusMustConflict() {

        final Shipment dispatched = shipment(ShipmentStatus.DISPATCHED, "op-dispatch");
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willReturn(dispatched);
        given(saveShipmentOutPort.findOperation("op-dispatch"))
                .willReturn(operation("op-dispatch", ShipmentStatus.PENDING, ShipmentStatus.DISPATCHED));

        assertThatThrownBy(() -> useCase.advanceShipmentStatus(SHIPMENT, "op-dispatch", ShipmentStatus.DISPATCHED))
                .as("operationId replay must validate the original command fingerprint")
                .isInstanceOf(ShipmentConflictException.class)
                .hasMessageContaining("operation");
    }

    @Test
    void earlierOperationIdMustReplayHistoricalResultAfterLaterShipmentTransition() {

        final AtomicReference<Shipment> current = new AtomicReference<>(shipment(ShipmentStatus.PENDING, null));
        final java.util.Map<String, ShipmentOperation> operations = new java.util.HashMap<>();
        given(findShipmentOutPort.findByShipmentNumber(SHIPMENT)).willAnswer(invocation -> current.get());
        given(saveShipmentOutPort.findOperation(any())).willAnswer(invocation -> operations.get(invocation.getArgument(0)));
        given(saveShipmentOutPort.save(any())).willAnswer(invocation -> {
            final Shipment saved = invocation.getArgument(0);
            current.set(saved);
            return saved;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            final ShipmentOperation operation = invocation.getArgument(0);
            operations.put(operation.getOperationId(), operation);
            return null;
        }).when(saveShipmentOutPort).saveOperation(any());

        final Shipment dispatched = useCase.advanceShipmentStatus(SHIPMENT, "op-dispatch", ShipmentStatus.PENDING);
        assertThat(dispatched.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);

        final Shipment inTransit = useCase.advanceShipmentStatus(SHIPMENT, "op-transit", ShipmentStatus.DISPATCHED);
        assertThat(inTransit.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);

        final Shipment replay = useCase.advanceShipmentStatus(SHIPMENT, "op-dispatch", ShipmentStatus.PENDING);

        assertThat(replay.getStatus())
                .as("retry of an earlier operation must return that operation's durable historical result")
                .isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(replay.getLastOperationId()).isEqualTo("op-dispatch");
    }

    private static ShipmentOperation operation(
            final String operationId,
            final ShipmentStatus expectedStatus,
            final ShipmentStatus resultStatus) {

        return ShipmentOperation.builder()
                .operationId(operationId)
                .shipmentNumber(SHIPMENT)
                .expectedStatus(expectedStatus)
                .resultStatus(resultStatus)
                .resultVersion(0)
                .build();
    }

    private static Shipment shipment(final ShipmentStatus status, final String lastOperationId) {

        return Shipment.builder()
                .shipmentNumber(SHIPMENT)
                .orderNumber(ORDER)
                .carrier("DHL")
                .trackingNumber("TRACK-1")
                .status(status)
                .createdDate(CREATED)
                .version(0)
                .lastOperationId(lastOperationId)
                .build();
    }
}
