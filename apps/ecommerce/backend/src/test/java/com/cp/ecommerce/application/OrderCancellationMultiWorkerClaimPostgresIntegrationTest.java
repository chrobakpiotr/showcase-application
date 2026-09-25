package com.cp.ecommerce.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRecoveryOutPort;

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

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "notification.retry.enabled=false",
                "order.cancellation.recovery.poll-interval-ms=3600000" })
class OrderCancellationMultiWorkerClaimPostgresIntegrationTest {

    private static final long SCHEDULER_ISOLATION_SECONDS = 86_400L;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private OutboxEventEntityRepository repository;
    @Autowired
    private ManageOrderCancellationRecoveryOutPort recoveryOutPort;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void staleWorkerMustNotMutateSuccessOrFailureAfterLeaseTakeover() {

        // Normalize before the PostgreSQL round-trip: PostgreSQL/JDBC timestamp precision
        // can round nanoseconds upward, making an otherwise equal due-time appear future-due.
        final Instant wallClockNow = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        final Instant firstClaimAt = wallClockNow.plusSeconds(SCHEDULER_ISOLATION_SECONDS);
        final String orderNumber = "B01-" + UUID.randomUUID().toString().replace("-", "");
        repository.saveAndFlush(
                OutboxEventEntity.builder()
                        .orderNumber(orderNumber)
                        .status(OutboxEventStatus.CANCELLING)
                        .createdDate(wallClockNow.minusSeconds(1))
                        .nextAttemptDate(wallClockNow.minusSeconds(1))
                        .cancellationNextAttemptDate(firstClaimAt)
                        .build());

        final OrderCancellationRecoveryClaim ownerA = recoveryOutPort.claim(orderNumber, firstClaimAt);
        assertThat(ownerA).isNotNull();

        final Instant afterLeaseExpiry = firstClaimAt.plusSeconds(31);
        final OrderCancellationRecoveryClaim ownerB = recoveryOutPort.claim(orderNumber, afterLeaseExpiry);
        assertThat(ownerB).isNotNull();
        assertThat(ownerB.claimId()).isNotEqualTo(ownerA.claimId());

        recoveryOutPort.recordSuccess(orderNumber, ownerA.claimId());
        recoveryOutPort.recordFailure(orderNumber, ownerA.claimId(), "stale failure", afterLeaseExpiry);

        final OutboxEventEntity persisted = repository.findAll()
                .stream()
                .filter(event -> orderNumber.equals(event.getOrderNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(persisted.getCancellationClaimId()).isEqualTo(ownerB.claimId());
        assertThat(persisted.getCancellationAttempts()).isZero();
        assertThat(persisted.getCancellationLastError()).isNull();
        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
    }

    @Test
    void shouldAllowOnlyOneWorkerToOwnCancellationRecoveryLease() throws Exception {
        // Normalize before the PostgreSQL round-trip: PostgreSQL/JDBC timestamp precision
        // can round nanoseconds upward, making an otherwise equal due-time appear future-due.
        final Instant wallClockNow = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        final Instant claimAt = wallClockNow.plusSeconds(SCHEDULER_ISOLATION_SECONDS);
        final String orderNumber = "N15-" + UUID.randomUUID();
        repository.saveAndFlush(
                OutboxEventEntity.builder()
                        .orderNumber(orderNumber)
                        .status(OutboxEventStatus.CANCELLING)
                        .createdDate(wallClockNow.minusSeconds(1))
                        .nextAttemptDate(wallClockNow.minusSeconds(1))
                        .cancellationNextAttemptDate(claimAt)
                        .build());

        final Callable<OrderCancellationRecoveryClaim> claim = () -> recoveryOutPort.claim(orderNumber, claimAt);
        final List<OrderCancellationRecoveryClaim> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var firstWorker = executor.submit(claim);
            final var secondWorker = executor.submit(claim);
            results.add(firstWorker.get());
            results.add(secondWorker.get());
        }

        assertThat(results.stream().filter(Objects::nonNull).count()).isEqualTo(1L);
        final OrderCancellationRecoveryClaim winner = results.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        recoveryOutPort.recordFailure(orderNumber, winner.claimId(), "retry", claimAt);

        final OutboxEventEntity persisted = repository.findAll()
                .stream()
                .filter(event -> orderNumber.equals(event.getOrderNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(persisted.getCancellationClaimId()).isNull();
        assertThat(persisted.getCancellationAttempts()).isEqualTo(1);
        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
    }
}
