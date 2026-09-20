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
    void shouldAllowOnlyOneWorkerToOwnCancellationRecoveryLease() throws Exception {
        final Instant now = Instant.now();
        final String orderNumber = "N15-" + UUID.randomUUID();
        repository.saveAndFlush(
                OutboxEventEntity.builder()
                        .orderNumber(orderNumber)
                        .status(OutboxEventStatus.CANCELLING)
                        .createdDate(now.minusSeconds(1))
                        .nextAttemptDate(now.minusSeconds(1))
                        .cancellationNextAttemptDate(now.minusSeconds(1))
                        .build());

        final Callable<OrderCancellationRecoveryClaim> claim = () -> recoveryOutPort.claim(orderNumber, now);
        final List<OrderCancellationRecoveryClaim> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var firstWorker = executor.submit(claim);
            final var secondWorker = executor.submit(claim);
            results.add(firstWorker.get());
            results.add(secondWorker.get());
        }

        assertThat(results.stream().filter(Objects::nonNull).count()).isEqualTo(1L);
        final OrderCancellationRecoveryClaim winner = results.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        recoveryOutPort.recordFailure(orderNumber, winner.claimId(), "retry", now);

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
