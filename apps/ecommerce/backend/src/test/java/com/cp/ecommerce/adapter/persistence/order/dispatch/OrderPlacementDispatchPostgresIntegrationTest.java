package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

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

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "service.mail.enabled=false",
                "service.camel.enabled=false",
                "outbox.publisher.enabled=false",
                "order-placement.dispatch.enabled=false",
                "order-placement.dispatch.retry-delay-ms=0",
                "payment.reconciliation.enabled=false",
                "notification.retry.enabled=false" })
class OrderPlacementDispatchPostgresIntegrationTest {

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");
    @Autowired
    OrderPlacementDispatchManager manager;
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
        repository.deleteAll();
    }

    @Test
    void replayedEnqueueMustCreateExactlyTwoStableDispatchIntents() {
        final Order order = OrderBuilder.mockOrder();
        manager.enqueue(order);
        manager.enqueue(order);
        final List<OrderPlacementDispatchEntity> rows = repository
                .findAllByOrderNumberOrderByDispatchIdAsc(order.getOrderNumber());
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OrderPlacementDispatchEntity::getDispatchId)
                .containsExactly(
                        OrderPlacementDispatchManager.CAMEL_PREFIX + order.getOrderNumber(),
                        OrderPlacementDispatchManager.EMAIL_PREFIX + order.getOrderNumber());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PENDING);
            assertThat(row.getAttempts()).isZero();
        });
    }

    @Test
    void concurrentClaimMustHaveOneOwnerAndRetrySameDispatchIdentity() throws Exception {
        final Order order = OrderBuilder.mockOrder();
        manager.enqueue(order);
        final String id = OrderPlacementDispatchManager.EMAIL_PREFIX + order.getOrderNumber();
        final Instant now = repository.findById(id).orElseThrow().getNextAttemptDate();
        final CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<OrderPlacementDispatchManager.DispatchClaim> first = executor.submit(() -> {
                start.await();
                return manager.claimDispatch(id, now);
            });
            final Future<OrderPlacementDispatchManager.DispatchClaim> second = executor.submit(() -> {
                start.await();
                return manager.claimDispatch(id, now);
            });
            start.countDown();
            final List<OrderPlacementDispatchManager.DispatchClaim> winners = java.util.stream.Stream
                    .of(first.get(), second.get())
                    .filter(Objects::nonNull)
                    .toList();
            assertThat(winners).hasSize(1);
            final var owner = winners.getFirst();
            manager.markFailed(owner, "smtp outcome unknown", now);
            final var retry = manager.claimDispatch(id, now.plusSeconds(1));
            assertThat(retry).isNotNull();
            assertThat(retry.dispatchId()).isEqualTo(owner.dispatchId());
            assertThat(retry.claimId()).isNotEqualTo(owner.claimId());
            manager.markSent(retry, now.plusSeconds(2));
        }
        final var persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderPlacementDispatchStatus.SENT);
        assertThat(persisted.getAttempts()).isEqualTo(2);
        assertThat(persisted.getClaimId()).isNull();
    }
}
