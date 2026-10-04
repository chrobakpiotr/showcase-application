package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;

import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.BDDMockito.given;

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "service.mail.enabled=false",
                "service.camel.enabled=false",
                "outbox.publisher.enabled=false",
                "order-placement.dispatch.enabled=false",
                "order-placement.dispatch.retry-delay-ms=5000",
                "payment.reconciliation.enabled=false",
                "notification.retry.enabled=false" })
class ParkedDispatchQueuePostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @MockitoBean
    private Clock clock;
    @MockitoBean
    private ManageOrderUseCase manageOrderInPort;
    @MockitoBean
    private SendOrderConfirmationEmailInPort email;
    @MockitoBean
    private RouteOrderNotificationInPort routing;

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");
    @Autowired
    OrderPlacementDispatchEntityRepository repository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clean() {
        given(clock.instant()).willReturn(NOW);
        repository.deleteAll();
    }

    @Autowired
    private com.cp.ecommerce.domain.order.dispatch.port.incoming.GetParkedDispatchesInPort queue;

    @Test
    void parkedQueueMustBoundPagesOrderTiesAndHideUnsafeData() {
        for (int index = 0; index < 53; index++) {
            final var row = dispatch("row-" + String.format("%03d", index), OrderPlacementDispatchStatus.PARKED,
                    NOW.minusSeconds(index < 2 ? 120 : 60), 8);
            row.setLastError(index == 0 ? "ORDER_MISSING" : index == 1 ? "ATTEMPT_BUDGET_EXHAUSTED" : "smtp credential secret");
            row.setClaimId("provider claim must never escape");
            row.setNextAttemptDate(NOW);
            repository.saveAndFlush(row);
        }
        for (final var status : List.of(OrderPlacementDispatchStatus.PENDING, OrderPlacementDispatchStatus.FAILED,
                OrderPlacementDispatchStatus.DELIVERING, OrderPlacementDispatchStatus.SENT)) {
            repository.saveAndFlush(dispatch("not-parked-" + status, status, NOW.minusSeconds(1000), 1));
        }
        final var first = queue.getParkedDispatches(new com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery(0, 20));
        assertThat(first.content()).hasSize(20);
        assertThat(first.totalElements()).isEqualTo(53);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.oldestAgeSeconds()).isEqualTo(120L);
        assertThat(first.content()).extracting(row -> row.orderNumber())
                .containsExactly(java.util.stream.IntStream.range(0, 20).mapToObj(index -> "row-" + String.format("%03d", index)).toArray(String[]::new));
        assertThat(first.content().getFirst().reasonCode().name()).isEqualTo("ORDER_MISSING");
        assertThat(first.content().get(1).reasonCode().name()).isEqualTo("ATTEMPT_BUDGET_EXHAUSTED");
        assertThat(first.content().get(2).reasonCode().name()).isEqualTo("OTHER");
        final var last = queue.getParkedDispatches(new com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery(2, 20));
        assertThat(last.content()).hasSize(13);
        assertThat(last.content().getFirst().orderNumber()).isEqualTo("row-040");
        assertThat(last.oldestAgeSeconds()).isEqualTo(120L);
        assertThat(queue.getParkedDispatches(new com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery(0, 50)).content()).hasSize(50);
        final var empty = queue.getParkedDispatches(new com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery(1000, 50));
        assertThat(empty.content()).isEmpty();
        assertThat(empty.oldestAgeSeconds()).isEqualTo(120L);
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void parkedQueueMustObserveCommittedStateWithoutTakingClaimLocks() throws Exception {
        final var row = dispatch("concurrent", OrderPlacementDispatchStatus.PARKED, NOW.minusSeconds(30), 8);
        repository.saveAndFlush(row);
        final var changed = new CountDownLatch(1);
        final var commit = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var writer = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        final var locked = repository.findByIdForUpdate(row.getDispatchId()).orElseThrow();
                        locked.setStatus(OrderPlacementDispatchStatus.SENT);
                        repository.saveAndFlush(locked);
                        changed.countDown();
                        try {
                            if (!commit.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                                throw new IllegalStateException("test commit latch timed out");
                            }
                        } catch (final InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
            try {
                assertThat(changed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                final var reader = executor.submit(() -> queue.getParkedDispatches(
                        new com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery(0, 20)));
                assertThat(reader.get(5, java.util.concurrent.TimeUnit.SECONDS).content()).hasSize(1);
            } finally {
                commit.countDown();
            }
            writer.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        final var result = queue.getParkedDispatches(new com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery(0, 20));
        assertThat(result.content()).isEmpty();
        assertThat(result.oldestAgeSeconds()).isNull();
    }

    private static OrderPlacementDispatchEntity dispatch(final String orderNumber,
            final OrderPlacementDispatchStatus status, final Instant createdAt, final int attempts) {
        return OrderPlacementDispatchEntity.builder()
                .dispatchId(OrderPlacementDispatchManager.dispatchId(orderNumber, OrderPlacementDispatchType.CONFIRMATION_EMAIL))
                .orderNumber(orderNumber)
                .dispatchType(OrderPlacementDispatchType.CONFIRMATION_EMAIL)
                .status(status)
                .createdDate(createdAt)
                .nextAttemptDate(NOW)
                .attempts(attempts)
                .build();
    }

}
