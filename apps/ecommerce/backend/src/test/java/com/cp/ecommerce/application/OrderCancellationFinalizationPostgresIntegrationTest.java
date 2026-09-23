package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.order.CancelOrderWorkflow;
import com.cp.ecommerce.application.order.CancellationRecoveryOutcome;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationEventKey;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRecoveryOutPort;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "order.cancellation.recovery.lease-ms=30000",
                "order.cancellation.recovery.retry-backoff-ms=5000",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class OrderCancellationFinalizationPostgresIntegrationTest {

    private static final BigDecimal PRICE = BigDecimal.TEN;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private ManageStockInPort manageStockInPort;

    @MockitoSpyBean
    private ManagePaymentInPort managePaymentInPort;

    @MockitoSpyBean
    private SendNotificationInPort sendNotificationInPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;

    @Autowired
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Autowired
    private ManageOrderCancellationRecoveryOutPort cancellationRecoveryOutPort;

    @Autowired
    private OutboxEventEntityRepository outboxEventRepository;

    @Autowired
    private NotificationEntityRepository notificationRepository;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void staleOwnerAfterTakeoverMustReportLostClaimAndMustNotEnqueueNotification() {
        final String sku = "S22A-" + compactUuid();
        final String orderNumber = prepareCancellingCapturedOrder(sku);
        final OrderCancellationRecoveryClaim ownerA = requireClaim(orderNumber);
        final AtomicReference<OrderCancellationRecoveryClaim> ownerB = new AtomicReference<>();

        doAnswer(invocation -> {
            final PaymentTransaction result = (PaymentTransaction) invocation.callRealMethod();
            ownerB.set(takeOverAfterExpiry(orderNumber));
            return result;
        }).when(managePaymentInPort).refundPayment(orderNumber);

        final CancellationRecoveryOutcome outcome = cancelOrderWorkflow.recoverCancellation(orderNumber, ownerA.claimId());

        assertThat(outcome).as("stale owner A must not report COMPLETED after owner B takes over before finalization")
                .isEqualTo(CancellationRecoveryOutcome.LOST_CLAIM);
        assertThat(ownerB.get()).isNotNull();
        assertThat(findEvent(orderNumber).getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        assertThat(findEvent(orderNumber).getCancellationClaimId()).isEqualTo(ownerB.get().claimId());
        assertThat(notificationRepository.findByEventKey(eventKey(orderNumber)))
                .as("stale owner must not enqueue ORDER_CANCELLED notification")
                .isEmpty();
    }

    @Test
    void notificationFailureAfterFencedStateChangeMustRollbackStateAndRetryExactlyOnce() {
        final String sku = "S22B-" + compactUuid();
        final String orderNumber = prepareCancellingCapturedOrder(sku);
        final OrderCancellationRecoveryClaim owner = requireClaim(orderNumber);
        final AtomicInteger notificationAttempts = new AtomicInteger();

        doAnswer(invocation -> {
            if (notificationAttempts.getAndIncrement() == 0) {
                assertThat(findEvent(orderNumber).getStatus())
                        .as("notification enqueue must observe fenced CANCELLED state inside the same transaction")
                        .isEqualTo(OutboxEventStatus.CANCELLED);
                throw new IllegalStateException("simulated notification persistence failure");
            }
            return invocation.callRealMethod();
        }).when(sendNotificationInPort)
                .sendNotification(anyString(), anyString(), eq(NotificationType.ORDER_CANCELLED), anyString(), anyString());

        assertThatThrownBy(() -> cancelOrderWorkflow.recoverCancellation(orderNumber, owner.claimId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated notification persistence failure");

        assertThat(findEvent(orderNumber).getStatus()).as("notification failure must roll back terminal saga state")
                .isEqualTo(OutboxEventStatus.CANCELLING);
        assertThat(findEvent(orderNumber).getCancellationClaimId()).isEqualTo(owner.claimId());
        assertThat(notificationRepository.findByEventKey(eventKey(orderNumber))).isEmpty();

        assertThat(cancelOrderWorkflow.recoverCancellation(orderNumber, owner.claimId()))
                .isEqualTo(CancellationRecoveryOutcome.COMPLETED);

        assertThat(findEvent(orderNumber).getStatus()).isEqualTo(OutboxEventStatus.CANCELLED);
        assertThat(notificationRepository.findByEventKey(eventKey(orderNumber))).isPresent();
        assertThat(notificationAttempts).hasValue(2);
    }

    private String prepareCancellingCapturedOrder(final String sku) {
        catalogProductFixture.ensureActiveProduct(sku, "S22-05 cancellation fixture", PRICE);
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();
        managePaymentInPort.capturePayment(orderNumber, PRICE, PaymentMethod.CARD);

        doThrow(new IllegalStateException("simulated stock release failure")).doCallRealMethod()
                .when(manageStockInPort)
                .releaseStock(anyString(), eq(sku));

        assertThatThrownBy(() -> cancelOrderWorkflow.cancelOrder(orderNumber)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated stock release failure");
        assertThat(findEvent(orderNumber).getStatus()).isEqualTo(OutboxEventStatus.CANCELLING);
        return orderNumber;
    }

    private OrderCancellationRecoveryClaim requireClaim(final String orderNumber) {
        final OrderCancellationRecoveryClaim claim = cancellationRecoveryOutPort.claim(orderNumber, clock.instant());
        assertThat(claim).isNotNull();
        return claim;
    }

    private OrderCancellationRecoveryClaim takeOverAfterExpiry(final String orderNumber) {
        final OutboxEventEntity event = findEvent(orderNumber);
        event.setCancellationClaimUntil(clock.instant().minusMillis(1));
        outboxEventRepository.saveAndFlush(event);
        final OrderCancellationRecoveryClaim claim = cancellationRecoveryOutPort.claim(orderNumber, clock.instant());
        assertThat(claim).as("worker B must acquire the expired recovery lease").isNotNull();
        return claim;
    }

    private OutboxEventEntity findEvent(final String orderNumber) {
        return outboxEventRepository.findAll()
                .stream()
                .filter(event -> orderNumber.equals(event.getOrderNumber()))
                .findFirst()
                .orElseThrow();
    }

    private static String eventKey(final String orderNumber) {
        return NotificationEventKey.of("order", orderNumber, NotificationType.ORDER_CANCELLED, "cancellation-v1");
    }

    private static OrderResource request(final String sku) {
        return new OrderResource(
                "S22-05 cancellation",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "S22-05 Buyer",
                        compactUuid() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "S22-05 cancellation fixture", PRICE, 1, null)),
                PaymentMethod.CARD,
                null);
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
