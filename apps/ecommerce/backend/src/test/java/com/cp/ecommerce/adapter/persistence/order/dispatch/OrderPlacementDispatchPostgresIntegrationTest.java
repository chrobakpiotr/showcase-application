package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.Order;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
class OrderPlacementDispatchPostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final String HEALTHY_ORDER = "z-healthy";

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
        given(clock.instant()).willReturn(NOW);
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
            final var retry = manager.claimDispatch(id, now.plusSeconds(5));
            assertThat(retry).isNotNull();
            assertThat(retry.dispatchId()).isEqualTo(owner.dispatchId());
            assertThat(retry.claimId()).isNotEqualTo(owner.claimId());
            manager.markSent(retry, now.plusSeconds(6));
        }
        final var persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderPlacementDispatchStatus.SENT);
        assertThat(persisted.getAttempts()).isEqualTo(2);
        assertThat(persisted.getClaimId()).isNull();
    }

    @Test
    void fiftyPoisonDispatchesMustNotStarveHealthyDispatchOnSecondDueTick() {
        final List<OrderPlacementDispatchEntity> rows = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            rows.add(dispatch("poison-" + String.format("%03d", index), OrderPlacementDispatchStatus.FAILED,
                    NOW.minusSeconds(100), 1));
        }
        final OrderPlacementDispatchEntity healthy = dispatch(HEALTHY_ORDER, OrderPlacementDispatchStatus.PENDING,
                NOW, 0);
        rows.add(healthy);
        repository.saveAllAndFlush(rows);
        given(manageOrderInPort.findOrder(anyString()))
                .willAnswer(invocation -> Order.builder().orderNumber(invocation.getArgument(0)).build());
        doAnswer(invocation -> {
            final Order order = invocation.getArgument(0);
            if (!HEALTHY_ORDER.equals(order.getOrderNumber())) {
                throw new IllegalStateException("poison dispatch failure");
            }
            return null;
        }).when(email).sendConfirmationEmail(any(Order.class));

        manager.retryDueDispatches();
        verify(email, times(50)).sendConfirmationEmail(any(Order.class));
        verify(email, never()).sendConfirmationEmail(argThat(order -> HEALTHY_ORDER.equals(order.getOrderNumber())));
        assertThat(repository.findById(healthy.getDispatchId()).orElseThrow().getStatus())
                .isEqualTo(OrderPlacementDispatchStatus.PENDING);
        for (int index = 0; index < 50; index++) {
            final var poison = repository.findById(rows.get(index).getDispatchId()).orElseThrow();
            assertThat(poison.getNextAttemptDate()).isEqualTo(NOW.plusSeconds(10));
            assertThat(poison.getAttempts()).isEqualTo(2);
        }

        given(clock.instant()).willReturn(NOW.plusSeconds(5));
        manager.retryDueDispatches();
        final var sent = repository.findById(healthy.getDispatchId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(OrderPlacementDispatchStatus.SENT);
        assertThat(sent.getAttempts()).isEqualTo(1);
        assertThat(sent.getSentDate()).isEqualTo(NOW.plusSeconds(5));
        assertThat(sent.getClaimId()).isNull();
        assertThat(sent.getClaimUntil()).isNull();
        assertThat(repository.count()).isEqualTo(51);
        verify(email, times(51)).sendConfirmationEmail(any(Order.class));
        verify(email).sendConfirmationEmail(argThat(order -> HEALTHY_ORDER.equals(order.getOrderNumber())));
        verify(routing, never()).routeNotification(any(Order.class));
        for (int index = 0; index < 50; index++) {
            final var poison = repository.findById(rows.get(index).getDispatchId()).orElseThrow();
            assertThat(poison.getStatus()).isEqualTo(OrderPlacementDispatchStatus.FAILED);
            assertThat(poison.getClaimId()).isNull();
            assertThat(poison.getClaimUntil()).isNull();
        }
    }

    @Test
    void exhaustedAmbiguousDispatchMustParkWithoutNinthExternalAttempt() {
        final var row = dispatch("budget", OrderPlacementDispatchStatus.FAILED, NOW, 7);
        repository.saveAndFlush(row);
        final Order order = Order.builder().orderNumber(row.getOrderNumber()).build();
        given(manageOrderInPort.findOrder(row.getOrderNumber())).willReturn(order);
        doAnswer(invocation -> {
            throw new IllegalStateException("unknown delivery outcome");
        }).when(email).sendConfirmationEmail(order);
        manager.retryDueDispatches();
        final var parked = repository.findById(row.getDispatchId()).orElseThrow();
        assertThat(parked.getStatus().name()).isEqualTo("PARKED");
        assertThat(parked.getAttempts()).isEqualTo(8);
        assertThat(parked.getClaimId()).isNull();
        assertThat(parked.getClaimUntil()).isNull();
        given(clock.instant()).willReturn(NOW.plusSeconds(600));
        manager.retryDueDispatches();
        manager.deliverDueDispatch(row.getDispatchId());
        verify(email).sendConfirmationEmail(order);
        assertThat(repository.findById(row.getDispatchId()).orElseThrow().getAttempts()).isEqualTo(8);
    }

    @Test
    void missingOrderMustParkBeforeCallingEitherDownstreamPort() {
        final var row = dispatch("missing-order", OrderPlacementDispatchStatus.PENDING, NOW, 0);
        repository.saveAndFlush(row);
        manager.retryDueDispatches();
        final var parked = repository.findById(row.getDispatchId()).orElseThrow();
        assertThat(parked.getStatus().name()).isEqualTo("PARKED");
        assertThat(parked.getAttempts()).isEqualTo(1);
        assertThat(parked.getClaimId()).isNull();
        assertThat(parked.getClaimUntil()).isNull();
        verify(email, never()).sendConfirmationEmail(any());
        verify(routing, never()).routeNotification(any());
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
