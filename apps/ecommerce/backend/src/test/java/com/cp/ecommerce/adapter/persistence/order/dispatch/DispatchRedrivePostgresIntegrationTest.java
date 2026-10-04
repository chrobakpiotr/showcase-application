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
                "order-placement.dispatch.max-attempts=2",
                "payment.reconciliation.enabled=false",
                "notification.retry.enabled=false" })
class DispatchRedrivePostgresIntegrationTest {

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
        audits.deleteAll();
        repository.deleteAll();
    }

    @Autowired
    private com.cp.ecommerce.domain.order.dispatch.port.incoming.RedriveDispatchInPort redrive;
    @Autowired
    private DispatchRedriveAuditRepository audits;
    @Autowired
    private OrderPlacementDispatchManager manager;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void redriveMustAuditPreviousCycleReplaySafelyAndBoundNextCycle() {
        final var row = parked("redrive", "ATTEMPT_BUDGET_EXHAUSTED");
        final var command = command("command", row.getDispatchId(), "operator", " inspected ");
        assertThat(redrive.redrive(command).name()).isEqualTo("REQUEUED");
        assertThat(redrive.redrive(command).name()).isEqualTo("REPLAYED");
        final var reset = repository.findById(row.getDispatchId()).orElseThrow();
        assertThat(reset.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PENDING);
        assertThat(reset.getAttempts()).isZero();
        assertThat(reset.getCreatedDate()).isEqualTo(row.getCreatedDate());
        assertThat(reset.getDispatchType()).isEqualTo(row.getDispatchType());
        assertThat(reset.getOrderNumber()).isEqualTo(row.getOrderNumber());
        final var audit = audits.findById("command").orElseThrow();
        assertThat(audit.getPreviousAttempts()).isEqualTo(8);
        assertThat(audit.getPreviousReason()).isEqualTo("ATTEMPT_BUDGET_EXHAUSTED");
        assertThat(audit.getReason()).isEqualTo("inspected");
        assertThat(audit.getActor()).isEqualTo("operator");
        assertThat(audit.getOriginalCreatedAt()).isEqualTo(row.getCreatedDate());
        for (final var changed : List.of(command("command", "different", "operator", "inspected"),
                command("command", row.getDispatchId(), "other", "inspected"),
                command("command", row.getDispatchId(), "operator", "different"))) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> redrive.redrive(changed))
                    .isInstanceOf(com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException.class);
        }
        final var order = com.cp.ecommerce.domain.order.Order.builder().orderNumber(row.getOrderNumber()).build();
        given(manageOrderInPort.findOrder(row.getOrderNumber())).willReturn(order);
        org.mockito.Mockito.doThrow(new IllegalStateException("unknown SMTP outcome")).when(email).sendConfirmationEmail(order);
        manager.retryDueDispatches();
        given(clock.instant()).willReturn(NOW.plusSeconds(5));
        manager.retryDueDispatches();
        assertThat(repository.findById(row.getDispatchId()).orElseThrow().getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        assertThat(repository.findById(row.getDispatchId()).orElseThrow().getAttempts()).isEqualTo(2);
        assertThat(redrive.redrive(command).name()).isEqualTo("REPLAYED");
        assertThat(repository.findById(row.getDispatchId()).orElseThrow().getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        assertThat(redrive.redrive(command("next-cycle", row.getDispatchId(), "operator", "reinspected")).name()).isEqualTo("REQUEUED");
        assertThat(audits.count()).isEqualTo(2);
        org.mockito.Mockito.verify(email, org.mockito.Mockito.times(2)).sendConfirmationEmail(order);
        org.mockito.Mockito.verifyNoInteractions(routing);
    }

    @Test
    void concurrentSameCommandMustReplayAndDifferentCommandsMustHaveOneWinner() throws Exception {
        final var row = parked("same", "ATTEMPT_BUDGET_EXHAUSTED");
        final var same = command("same-command", row.getDispatchId(), "operator", "inspected");
        final var outcomes = race(same, same);
        assertThat(outcomes).containsExactlyInAnyOrder("REQUEUED", "REPLAYED");
        assertThat(audits.count()).isEqualTo(1);
        final var other = parked("different", "ATTEMPT_BUDGET_EXHAUSTED");
        assertThat(race(command("a", other.getDispatchId(), "operator", "inspected"),
                command("b", other.getDispatchId(), "operator", "inspected")))
                .containsExactlyInAnyOrder("REQUEUED", "CONFLICT");
        assertThat(audits.count()).isEqualTo(2);
    }

    @Test
    void redriveMustRejectIneligibleReasonsAndRollbackAuditWithTransition() {
        for (final String reason : List.of("ORDER_MISSING", "OTHER", "provider secret")) {
            final var row = parked("ineligible-" + reason.hashCode(), reason);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> redrive.redrive(command(reason, row.getDispatchId(), "operator", "inspected")))
                    .isInstanceOf(com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException.class);
            assertThat(repository.findById(row.getDispatchId()).orElseThrow().getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        }
        final var row = parked("rollback", "ATTEMPT_BUDGET_EXHAUSTED");
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            redrive.redrive(command("rollback", row.getDispatchId(), "operator", "inspected"));
            status.setRollbackOnly();
        });
        assertThat(audits.count()).isZero();
        assertThat(repository.findById(row.getDispatchId()).orElseThrow().getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        assertThat(repository.findById(row.getDispatchId()).orElseThrow().getAttempts()).isEqualTo(8);
        org.mockito.Mockito.verifyNoInteractions(email, routing);
    }

    @Test
    void redriveMustRejectParkedRowsWithResidualClaimMarkers() {
        final var row = parked("residual-claim", "ATTEMPT_BUDGET_EXHAUSTED");
        row.setClaimId("unexpected-owner");
        row.setClaimUntil(NOW.plusSeconds(30));
        repository.saveAndFlush(row);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> redrive.redrive(
                command("residual-claim-command", row.getDispatchId(), "operator", "inspected")))
                .isInstanceOf(com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException.class);
        final var persisted = repository.findById(row.getDispatchId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        assertThat(persisted.getClaimId()).isEqualTo("unexpected-owner");
        assertThat(persisted.getClaimUntil()).isEqualTo(NOW.plusSeconds(30));
        assertThat(audits.count()).isZero();
    }

    @Test
    void schedulerClaimMustSerializeWithRedriveAndStaleOwnerMustNotFinalize() throws Exception {
        final var row = parked("claim-race", "ATTEMPT_BUDGET_EXHAUSTED");
        final var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var commandResult = executor.submit(() -> {
                start.await();
                return redrive.redrive(command("race", row.getDispatchId(), "operator", "inspected"));
            });
            final var claimResult = executor.submit(() -> {
                start.await();
                return manager.claimDispatch(row.getDispatchId(), NOW);
            });
            start.countDown();
            assertThat(commandResult.get(10, java.util.concurrent.TimeUnit.SECONDS).name()).isEqualTo("REQUEUED");
            final var racedClaim = claimResult.get(10, java.util.concurrent.TimeUnit.SECONDS);
            final var claim = racedClaim == null ? manager.claimDispatch(row.getDispatchId(), NOW) : racedClaim;
            assertThat(claim).isNotNull();
            final var stale = new OrderPlacementDispatchManager.DispatchClaim(row.getDispatchId(), row.getOrderNumber(), row.getDispatchType(), "previous-cycle");
            manager.markSent(stale, NOW);
            manager.markFailed(stale, "stale", NOW);
            final var persisted = repository.findById(row.getDispatchId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(OrderPlacementDispatchStatus.DELIVERING);
            assertThat(persisted.getAttempts()).isEqualTo(1);
            assertThat(persisted.getClaimId()).isEqualTo(claim.claimId());
            manager.markSent(claim, NOW);
        }
        org.mockito.Mockito.verifyNoInteractions(email, routing);
    }

    private List<String> race(final com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand first,
            final com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand second) throws Exception {
        final var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var futures = List.of(first, second).stream().map(command -> executor.submit(() -> {
                start.await();
                try {
                    return redrive.redrive(command).name();
                } catch (final com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException exception) {
                    return "CONFLICT";
                }
            })).toList();
            start.countDown();
            return List.of(futures.getFirst().get(10, java.util.concurrent.TimeUnit.SECONDS), futures.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private OrderPlacementDispatchEntity parked(final String order, final String reason) {
        final var row = dispatch(order, OrderPlacementDispatchStatus.PARKED, NOW.minusSeconds(100), 8);
        row.setLastError(reason);
        return repository.saveAndFlush(row);
    }

    private static com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand command(final String id,
            final String dispatch, final String actor, final String reason) {
        return new com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand(id, dispatch, actor, reason);
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
