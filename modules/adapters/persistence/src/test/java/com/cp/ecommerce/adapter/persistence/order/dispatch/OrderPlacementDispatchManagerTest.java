package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.LogCapture;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacementDispatchManagerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private static final String OWNER_ID = "owner";
    private static final String BUDGET_DISPATCH_ID = "budget";
    private static final String FAILED_DISPATCH_ID = "fail";
    @Mock
    OrderPlacementDispatchEntityRepository repository;
    @Mock
    EntityManager entityManager;
    @Mock
    Query nativeQuery;
    @Mock
    ManageOrderInPort manageOrderInPort;
    @Mock
    SendOrderConfirmationEmailInPort email;
    @Mock
    RouteOrderNotificationInPort camel;
    @Mock
    TransactionOperations tx;
    @Mock
    TransactionStatus txStatus;
    private OrderPlacementDispatchManager manager;

    @BeforeEach
    void setUp() {
        manager = new OrderPlacementDispatchManager(
                repository,
                entityManager,
                manageOrderInPort,
                email,
                camel,
                tx,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(tx.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(txStatus));
        lenient().doAnswer(invocation -> {
            ((java.util.function.Consumer<TransactionStatus>) invocation.getArgument(0)).accept(txStatus);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    @Test
    void shouldEnqueueTwoStableIntents() {
        final Order order = OrderBuilder.mockOrder();
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), any())).willReturn(nativeQuery);
        given(nativeQuery.executeUpdate()).willReturn(1, 0);
        manager.enqueue(order);
        verify(nativeQuery, org.mockito.Mockito.times(2)).executeUpdate();
        verify(nativeQuery).setParameter("dispatchId", OrderPlacementDispatchManager.EMAIL_PREFIX + order.getOrderNumber());
        verify(nativeQuery).setParameter("dispatchId", OrderPlacementDispatchManager.CAMEL_PREFIX + order.getOrderNumber());
    }

    @Test
    void shouldClaimDueAndFenceUnavailableRows() {
        final OrderPlacementDispatchEntity due = dispatch("due", OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        given(repository.findByIdForUpdate("due")).willReturn(Optional.of(due));
        final var claim = manager.claimDispatch("due", NOW);
        assertThat(claim).isNotNull();
        assertThat(due.getStatus()).isEqualTo(OrderPlacementDispatchStatus.DELIVERING);
        assertThat(due.getAttempts()).isEqualTo(1);
        verify(repository).saveAndFlush(due);

        final OrderPlacementDispatchEntity sent = dispatch("sent", OrderPlacementDispatchType.CAMEL_ROUTING);
        sent.setStatus(OrderPlacementDispatchStatus.SENT);
        given(repository.findByIdForUpdate("sent")).willReturn(Optional.of(sent));
        assertThat(manager.claimDispatch("sent", NOW)).isNull();

        final OrderPlacementDispatchEntity future = dispatch("future", OrderPlacementDispatchType.CAMEL_ROUTING);
        future.setNextAttemptDate(NOW.plusSeconds(60));
        given(repository.findByIdForUpdate("future")).willReturn(Optional.of(future));
        assertThat(manager.claimDispatch("future", NOW)).isNull();

        given(repository.findByIdForUpdate("missing")).willReturn(Optional.empty());
        assertThat(manager.claimDispatch("missing", NOW)).isNull();
    }

    @Test
    void shouldDeliverEmailAndCamelAndMarkSent() {
        final Order order = OrderBuilder.mockOrder();
        final OrderPlacementDispatchEntity emailRow = dispatch("email", OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        given(repository.findByIdForUpdate("email")).willReturn(Optional.of(emailRow));
        given(manageOrderInPort.findOrder(order.getOrderNumber())).willReturn(order);
        manager.deliverDueDispatch("email");
        verify(email).sendConfirmationEmail(order);
        verify(camel, never()).routeNotification(any());
        assertThat(emailRow.getStatus()).isEqualTo(OrderPlacementDispatchStatus.SENT);

        final OrderPlacementDispatchEntity camelRow = dispatch("camel", OrderPlacementDispatchType.CAMEL_ROUTING);
        given(repository.findByIdForUpdate("camel")).willReturn(Optional.of(camelRow));
        manager.deliverDueDispatch("camel");
        verify(camel).routeNotification(order);
        assertThat(camelRow.getStatus()).isEqualTo(OrderPlacementDispatchStatus.SENT);
    }

    @Test
    void shouldPersistFailureAndIgnoreLostOwnerFinalization() {
        final Order order = OrderBuilder.mockOrder();
        final OrderPlacementDispatchEntity row = dispatch(FAILED_DISPATCH_ID, OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        given(repository.findByIdForUpdate(FAILED_DISPATCH_ID)).willReturn(Optional.of(row));
        given(manageOrderInPort.findOrder(order.getOrderNumber())).willReturn(order);
        doThrow(new IllegalStateException("smtp outcome unknown")).when(email).sendConfirmationEmail(order);
        try (LogCapture logs = new LogCapture(OrderPlacementDispatchManager.class)) {
            manager.deliverDueDispatch(FAILED_DISPATCH_ID);
            assertThat(logs.formattedMessages()).contains("Could not deliver durable placement dispatch")
                    .doesNotContain(FAILED_DISPATCH_ID, order.getOrderNumber(), "smtp outcome unknown");
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        }
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.FAILED);
        assertThat(row.getLastError()).contains("smtp outcome unknown");
        assertThat(row.getNextAttemptDate()).isEqualTo(NOW.plusMillis(manager.retryDelayMillis));

        final OrderPlacementDispatchEntity stale = dispatch("stale", OrderPlacementDispatchType.CAMEL_ROUTING);
        stale.setStatus(OrderPlacementDispatchStatus.DELIVERING);
        stale.setClaimId("owner-b");
        given(repository.findByIdForUpdate("stale")).willReturn(Optional.of(stale));
        final var staleClaim = new OrderPlacementDispatchManager.DispatchClaim(
                "stale",
                stale.getOrderNumber(),
                stale.getDispatchType(),
                "owner-a");
        manager.markSent(staleClaim, NOW);
        manager.markFailed(staleClaim, "ignored", NOW);
        manager.markParked(staleClaim, "ORDER_MISSING", NOW);
        assertThat(stale.getClaimId()).isEqualTo("owner-b");
    }

    @Test
    void shouldPollAndSkipAlreadyOwnedFutureLease() {
        given(repository.findDueDispatchIds(anyCollection(), eq(NOW), any(Pageable.class))).willReturn(List.of("owned"));
        final OrderPlacementDispatchEntity row = dispatch("owned", OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        row.setStatus(OrderPlacementDispatchStatus.DELIVERING);
        row.setNextAttemptDate(NOW.plusSeconds(10));
        given(repository.findByIdForUpdate("owned")).willReturn(Optional.of(row));
        manager.retryDueDispatches();
        verify(manageOrderInPort, never()).findOrder(anyString());
    }

    @Test
    void shouldBoundUnknownFailuresWithExponentialBackoffAndParkEighthAttempt() {
        final long[] delays = { 5_000, 10_000, 20_000, 40_000, 80_000, 160_000, 300_000 };
        for (int attempt = 1; attempt <= 8; attempt++) {
            final var row = dispatch("failure-" + attempt, OrderPlacementDispatchType.CONFIRMATION_EMAIL);
            row.setStatus(OrderPlacementDispatchStatus.DELIVERING);
            row.setAttempts(attempt);
            row.setClaimId(OWNER_ID);
            given(repository.findByIdForUpdate(row.getDispatchId())).willReturn(Optional.of(row));
            final var claim = new OrderPlacementDispatchManager.DispatchClaim(
                    row.getDispatchId(),
                    row.getOrderNumber(),
                    row.getDispatchType(),
                    OWNER_ID);
            manager.markFailed(claim, "unknown outcome", NOW);
            assertThat(row.getAttempts()).isEqualTo(attempt);
            assertThat(row.getClaimId()).isNull();
            assertThat(row.getClaimUntil()).isNull();
            assertThat(row.getStatus())
                    .isEqualTo(attempt == 8 ? OrderPlacementDispatchStatus.PARKED : OrderPlacementDispatchStatus.FAILED);
            if (attempt < 8) {
                assertThat(row.getNextAttemptDate()).isEqualTo(NOW.plusMillis(delays[attempt - 1]));
            }
        }
    }

    @Test
    void shouldAllowFinalClaimButNeverNinthClaimIncludingExpiredWorker() {
        final var row = dispatch(BUDGET_DISPATCH_ID, OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        row.setAttempts(7);
        given(repository.findByIdForUpdate(BUDGET_DISPATCH_ID)).willReturn(Optional.of(row));
        final var claim = manager.claimDispatch(BUDGET_DISPATCH_ID, NOW);
        assertThat(claim).isNotNull();
        assertThat(row.getAttempts()).isEqualTo(8);
        assertThat(manager.claimDispatch(BUDGET_DISPATCH_ID, NOW)).isNull();
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.DELIVERING);
        assertThat(manager.claimDispatch(BUDGET_DISPATCH_ID, row.getClaimUntil())).isNull();
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        assertThat(row.getAttempts()).isEqualTo(8);
        assertThat(row.getClaimId()).isNull();
        assertThat(manager.claimDispatch(BUDGET_DISPATCH_ID, NOW.plusSeconds(100))).isNull();
        manager.markSent(claim, NOW.plusSeconds(100));
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
    }

    @Test
    void shouldParkMissingOrderWithoutCallingDownstream() {
        final var row = dispatch("missing-order", OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        given(repository.findByIdForUpdate(row.getDispatchId())).willReturn(Optional.of(row));
        manager.deliverDueDispatch(row.getDispatchId());
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.PARKED);
        assertThat(row.getAttempts()).isEqualTo(1);
        verify(email, never()).sendConfirmationEmail(any());
        verify(camel, never()).routeNotification(any());
    }

    @Test
    void shouldCapLargeBaseWithoutOverflowAndHonorZeroDelay() {
        for (final long base : new long[] { 0, Long.MAX_VALUE }) {
            manager.retryDelayMillis = base;
            final var row = dispatch("base-" + base, OrderPlacementDispatchType.CAMEL_ROUTING);
            row.setStatus(OrderPlacementDispatchStatus.DELIVERING);
            row.setAttempts(7);
            row.setClaimId(OWNER_ID);
            given(repository.findByIdForUpdate(row.getDispatchId())).willReturn(Optional.of(row));
            manager.markFailed(
                    new OrderPlacementDispatchManager.DispatchClaim(
                            row.getDispatchId(),
                            row.getOrderNumber(),
                            row.getDispatchType(),
                            OWNER_ID),
                    "unknown",
                    NOW);
            assertThat(row.getNextAttemptDate()).isEqualTo(NOW.plusMillis(base == 0 ? 0 : 300_000));
        }
    }

    @Test
    void shouldRejectRetryBudgetsOutsidePolicyBounds() {
        manager.validateRetryConfiguration();
        manager.maxAttempts = 1;
        manager.validateRetryConfiguration();
        manager.maxAttempts = 9;
        assertThatThrownBy(manager::validateRetryConfiguration).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-attempts");
        manager.maxAttempts = 8;
        manager.retryDelayMillis = -1;
        assertThatThrownBy(manager::validateRetryConfiguration).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-delay-ms");
    }

    @Test
    void shouldRetryLookupExceptionAndAllowSuccessfulFinalAttempt() {
        final var row = dispatch("lookup", OrderPlacementDispatchType.CONFIRMATION_EMAIL);
        given(repository.findByIdForUpdate(row.getDispatchId())).willReturn(Optional.of(row));
        given(manageOrderInPort.findOrder(row.getOrderNumber())).willThrow(new IllegalStateException("missing"));
        manager.deliverDueDispatch(row.getDispatchId());
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.FAILED);
        assertThat(row.getNextAttemptDate()).isEqualTo(NOW.plusSeconds(5));
        verify(email, never()).sendConfirmationEmail(any());

        row.setAttempts(7);
        row.setNextAttemptDate(NOW);
        final var finalClaim = manager.claimDispatch(row.getDispatchId(), NOW);
        assertThat(finalClaim).isNotNull();
        manager.markSent(finalClaim, NOW);
        assertThat(row.getStatus()).isEqualTo(OrderPlacementDispatchStatus.SENT);
        assertThat(row.getAttempts()).isEqualTo(8);
    }

    private static OrderPlacementDispatchEntity dispatch(final String id, final OrderPlacementDispatchType type) {
        return OrderPlacementDispatchEntity.builder()
                .dispatchId(id)
                .orderNumber(OrderBuilder.TEST_ORDER_NUMBER)
                .dispatchType(type)
                .status(OrderPlacementDispatchStatus.PENDING)
                .createdDate(NOW)
                .attempts(0)
                .nextAttemptDate(NOW)
                .build();
    }
}
