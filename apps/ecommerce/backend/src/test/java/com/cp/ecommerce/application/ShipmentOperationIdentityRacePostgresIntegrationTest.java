package com.cp.ecommerce.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentEntityRepository;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntity;
import com.cp.ecommerce.adapter.persistence.shipment.entity.ShipmentOperationEntityRepository;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.shipment.ShipmentOperation;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "outbox.publisher.enabled=false")
class ShipmentOperationIdentityRacePostgresIntegrationTest {

    private static final Instant DISPATCHED = Instant.parse("2026-09-23T12:00:00Z");
    private static final Instant ESTIMATED = DISPATCHED.plusSeconds(432000);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private SaveShipmentOutPort saveShipmentOutPort;

    @Autowired
    private ShipmentEntityRepository shipmentRepository;

    @Autowired
    private ShipmentOperationEntityRepository operationRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void committedOperationIdMustNotBeReassignedToAnotherShipment() {
        final String operationId = "s22-03-seq-" + compactUuid();
        final String shipmentA = persistShipment();
        final String shipmentB = persistShipment();

        saveShipmentOutPort.saveOperation(operation(operationId, shipmentA));

        assertThatThrownBy(() -> saveShipmentOutPort.saveOperation(operation(operationId, shipmentB)))
                .isInstanceOf(ShipmentConflictException.class)
                .hasMessageContaining(operationId);

        final ShipmentOperationEntity canonical = operationRepository.findById(operationId).orElseThrow();
        assertThat(canonical.getShipmentNumber()).isEqualTo(shipmentA);
    }

    @Test
    void concurrentOperationIdCollisionMustHaveExactlyOneWinner() throws Exception {
        final String operationId = "s22-03-race-" + compactUuid();
        final String shipmentA = persistShipment();
        final String shipmentB = persistShipment();
        final CyclicBarrier start = new CyclicBarrier(2);
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            final Future<Attempt> first = executor.submit(() -> saveAfterBarrier(start, operation(operationId, shipmentA)));
            final Future<Attempt> second = executor.submit(() -> saveAfterBarrier(start, operation(operationId, shipmentB)));

            final List<Attempt> attempts = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(attempts.stream().filter(Attempt::succeeded).count()).isEqualTo(1);
            assertThat(attempts.stream().filter(attempt -> !attempt.succeeded()).count()).isEqualTo(1);
            assertThat(attempts.stream().filter(attempt -> !attempt.succeeded()).findFirst().orElseThrow().failure())
                    .isInstanceOf(ShipmentConflictException.class);

            final String winner = attempts.stream().filter(Attempt::succeeded).findFirst().orElseThrow().shipmentNumber();
            assertThat(operationRepository.findById(operationId).orElseThrow().getShipmentNumber()).isEqualTo(winner);
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt saveAfterBarrier(final CyclicBarrier start, final ShipmentOperation operation) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            saveShipmentOutPort.saveOperation(operation);
            return new Attempt(operation.getShipmentNumber(), null);
        } catch (RuntimeException exception) {
            return new Attempt(operation.getShipmentNumber(), exception);
        }
    }

    private String persistShipment() {
        final String shipmentNumber = "SHIP-" + UUID.randomUUID();
        final String orderNumber = "ORD-" + UUID.randomUUID();
        shipmentRepository.saveAndFlush(
                ShipmentEntity.builder()
                        .shipmentNumber(shipmentNumber)
                        .orderNumber(orderNumber)
                        .carrier("S22-03-CARRIER")
                        .trackingNumber("TRACK-" + compactUuid())
                        .status(ShipmentStatus.PENDING)
                        .createdDate(DISPATCHED.minusSeconds(60))
                        .version(0)
                        .build());
        return shipmentNumber;
    }

    private static ShipmentOperation operation(final String operationId, final String shipmentNumber) {
        return ShipmentOperation.builder()
                .operationId(operationId)
                .shipmentNumber(shipmentNumber)
                .expectedStatus(ShipmentStatus.PENDING)
                .resultStatus(ShipmentStatus.DISPATCHED)
                .dispatchedDate(DISPATCHED)
                .estimatedDeliveryDate(ESTIMATED)
                .resultVersion(1)
                .build();
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Attempt(String shipmentNumber, RuntimeException failure) {

        boolean succeeded() {
            return failure == null;
        }
    }
}
