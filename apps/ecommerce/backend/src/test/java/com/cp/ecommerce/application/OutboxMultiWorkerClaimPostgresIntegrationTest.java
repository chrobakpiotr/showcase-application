package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cp.ecommerce.adapter.persistence.order.dispatch.OrderPlacementDispatchManager;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderPlacementBestEffortTail;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderPlacementSagaOrchestrator;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.order.CancelOrderWorkflow;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
import com.cp.ecommerce.domain.order.port.incoming.DetectDuplicateOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false",
                "order.cancellation.recovery.poll-interval-ms=3600000",
                "notification.retry.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000" })
class OutboxMultiWorkerClaimPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Autowired
    private CatalogProductFixture catalogProductFixture;
    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private ManageOrderInPort manageOrderInPort;

    @MockitoSpyBean
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @MockitoSpyBean
    private ManagePaymentReconciliationOutPort paymentReconciliationOutPort;

    @MockitoSpyBean
    private RefundPaymentOutPort refundPaymentOutPort;

    @Autowired
    private PaymentReconciliationEntityRepository paymentReconciliationRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OutboxEventEntityRepository outboxEventEntityRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldAllowOnlyOneWorkerWhilePlacementLeaseIsActive() throws Exception {

        final String sku = "R03A-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);

        final CountDownLatch fulfillmentEntered = new CountDownLatch(1);
        final CountDownLatch releaseFulfillment = new CountDownLatch(1);
        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        doAnswer(invocation -> {
            fulfillmentEntered.countDown();
            if (!releaseFulfillment.await(10, TimeUnit.SECONDS)) {

                throw new IllegalStateException("Timed out waiting to release fulfillment");
            }
            return null;
        }).when(fulfillment).sendMessage(any(Order.class));

        final OrderPlacementSagaOrchestrator workerA = newOrchestrator(fulfillment);
        final OrderPlacementSagaOrchestrator workerB = newOrchestrator(fulfillment);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            final Future<?> firstPoll = executor.submit(workerA::publishPendingEvents);
            assertThat(fulfillmentEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(statusContains(OutboxEventStatus.PROCESSING, orderNumber)).isTrue();

            final Future<?> secondPoll = executor.submit(workerB::publishPendingEvents);
            secondPoll.get(10, TimeUnit.SECONDS);

            verify(fulfillment, times(1)).sendMessage(any(Order.class));

            releaseFulfillment.countDown();
            firstPoll.get(10, TimeUnit.SECONDS);
        }

        assertThat(statusContains(OutboxEventStatus.SENT, orderNumber)).isTrue();
        assertThat(statusContains(OutboxEventStatus.PROCESSING, orderNumber)).isFalse();
        verify(fulfillment, times(1)).sendMessage(any(Order.class));
    }

    @Test
    void shouldNotRefundLateCaptureAfterHealthyTakeover() throws Exception {

        final String sku = "A1Q01-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);

        final CountDownLatch firstCaptureReturned = new CountDownLatch(1);
        final CountDownLatch releaseLateWorker = new CountDownLatch(1);
        final AtomicInteger captureCalls = new AtomicInteger();

        doAnswer(invocation -> {
            final PaymentTransaction result = (PaymentTransaction) invocation.callRealMethod();
            if (captureCalls.incrementAndGet() == 1) {
                firstCaptureReturned.countDown();
                if (!releaseLateWorker.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release late capture worker");
                }
            }
            return result;
        }).when(managePaymentInPort).capturePayment(eq(orderNumber), any(BigDecimal.class), any(PaymentMethod.class));

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        final OrderPlacementSagaOrchestrator workerA = newOrchestrator(fulfillment);
        final OrderPlacementSagaOrchestrator workerB = newOrchestrator(fulfillment);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<?> firstPoll = executor.submit(workerA::publishPendingEvents);
            assertThat(firstCaptureReturned.await(10, TimeUnit.SECONDS)).isTrue();

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                final OutboxEventEntity processing = outboxEventEntityRepository
                        .findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PROCESSING)
                        .stream()
                        .filter(event -> event.getOrderNumber().equals(orderNumber))
                        .findFirst()
                        .orElseThrow();
                processing.setClaimUntil(Instant.EPOCH);
                outboxEventEntityRepository.save(processing);
            });

            final Future<?> secondPoll = executor.submit(workerB::publishPendingEvents);
            secondPoll.get(10, TimeUnit.SECONDS);

            assertThat(statusContains(OutboxEventStatus.SENT, orderNumber)).isTrue();
            verify(fulfillment, times(1)).sendMessage(any(Order.class));

            releaseLateWorker.countDown();
            firstPoll.get(10, TimeUnit.SECONDS);
        }

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(managePaymentInPort, never()).refundPayment(orderNumber);
        verify(fulfillment, times(1)).sendMessage(any(Order.class));
    }

    @Test
    void cancellationWinningBeforeCapturePrepareMustCompensateLateCapture() throws Exception {

        final String sku = "S22-F1-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);

        final CountDownLatch beforePrepare = new CountDownLatch(1);
        final CountDownLatch resumeCapture = new CountDownLatch(1);

        doAnswer(invocation -> {
            beforePrepare.countDown();
            if (!resumeCapture.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to resume late capture");
            }
            return invocation.callRealMethod();
        }).when(managePaymentInPort).capturePayment(eq(orderNumber), any(BigDecimal.class), any(PaymentMethod.class));

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        final OrderPlacementSagaOrchestrator worker = newOrchestrator(fulfillment);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<?> running = executor.submit(worker::publishPendingEvents);
            try {
                assertThat(beforePrepare.await(10, TimeUnit.SECONDS)).isTrue();

                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    final OutboxEventEntity row = outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                            .orElseThrow();
                    row.setClaimUntil(Instant.EPOCH);
                    outboxEventEntityRepository.save(row);
                });

                cancelOrderWorkflow.cancelOrder(orderNumber);
                assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
            } finally {
                resumeCapture.countDown();
            }
            running.get(20, TimeUnit.SECONDS);
        }

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus())
                .as("late capture must be compensated when durable cancellation already won")
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
        verify(fulfillment, never()).sendMessage(any(Order.class));
    }

    @Test
    void captureFinalizationFailureAfterCancellationMustRecoverAndRefund() throws Exception {
        final String sku = "S22-RF1-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);
        final String captureOperationId = "ORDER-CAPTURE:" + orderNumber;
        final CountDownLatch beforePrepare = new CountDownLatch(1);
        final CountDownLatch resumeCapture = new CountDownLatch(1);
        final AtomicInteger completionCalls = new AtomicInteger();

        doAnswer(invocation -> {
            if (completionCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("injected capture completion persistence outage");
            }
            return invocation.callRealMethod();
        }).when(paymentReconciliationOutPort).complete(captureOperationId);
        doAnswer(invocation -> {
            beforePrepare.countDown();
            if (!resumeCapture.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to resume RF1 capture");
            }
            return invocation.callRealMethod();
        }).when(managePaymentInPort).capturePayment(eq(orderNumber), any(BigDecimal.class), any(PaymentMethod.class));

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        final OrderPlacementSagaOrchestrator worker = newOrchestrator(fulfillment);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<?> running = executor.submit(worker::publishPendingEvents);
            try {
                assertThat(beforePrepare.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(getPaymentInPort.getPayment(orderNumber).getCreated()).isNull();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    final OutboxEventEntity row = outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                            .orElseThrow();
                    row.setClaimUntil(Instant.EPOCH);
                    outboxEventEntityRepository.save(row);
                });
                cancelOrderWorkflow.cancelOrder(orderNumber);
                assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
            } finally {
                resumeCapture.countDown();
            }
            running.get(20, TimeUnit.SECONDS);
        }

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(paymentReconciliationRepository.findById(captureOperationId).orElseThrow().getStatus())
                .isEqualTo(PaymentReconciliationStatus.PENDING);
        final var due = paymentReconciliationRepository.findById(captureOperationId).orElseThrow();
        due.setNextAttemptDate(Instant.EPOCH);
        paymentReconciliationRepository.saveAndFlush(due);

        reconcilePaymentsOnce();

        assertThat(paymentReconciliationRepository.findById(captureOperationId).orElseThrow().getStatus())
                .isEqualTo(PaymentReconciliationStatus.COMPLETED);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus())
                .as("recovered capture must refund a durably cancelled order before capture reconciliation closes")
                .isEqualTo(PaymentStatus.REFUNDED);
        worker.publishPendingEvents();
        cancelOrderWorkflow.cancelOrder(orderNumber);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(fulfillment, never()).sendMessage(any(Order.class));
    }

    @Test
    void staleCaptureRecoveryOwnerMustNotStartRefundAfterTakeover() throws Exception {
        final String sku = "RF1C-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);
        final String captureOperationId = "ORDER-CAPTURE:" + orderNumber;
        final CountDownLatch beforePrepare = new CountDownLatch(1);
        final CountDownLatch resumeCapture = new CountDownLatch(1);
        final AtomicInteger completionCalls = new AtomicInteger();

        doAnswer(invocation -> {
            if (completionCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("injected capture completion persistence outage");
            }
            return invocation.callRealMethod();
        }).when(paymentReconciliationOutPort).complete(captureOperationId);
        doAnswer(invocation -> {
            beforePrepare.countDown();
            if (!resumeCapture.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to resume RF1-C capture");
            }
            return invocation.callRealMethod();
        }).when(managePaymentInPort).capturePayment(eq(orderNumber), any(BigDecimal.class), any(PaymentMethod.class));

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        final OrderPlacementSagaOrchestrator worker = newOrchestrator(fulfillment);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<?> running = executor.submit(worker::publishPendingEvents);
            try {
                assertThat(beforePrepare.await(10, TimeUnit.SECONDS)).isTrue();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    final OutboxEventEntity row = outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber)
                            .orElseThrow();
                    row.setClaimUntil(Instant.EPOCH);
                    outboxEventEntityRepository.save(row);
                });
                cancelOrderWorkflow.cancelOrder(orderNumber);
                assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
            } finally {
                resumeCapture.countDown();
            }
            running.get(20, TimeUnit.SECONDS);
        }

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        final var pending = paymentReconciliationRepository.findById(captureOperationId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PaymentReconciliationStatus.PENDING);
        pending.setNextAttemptDate(Instant.EPOCH);
        paymentReconciliationRepository.saveAndFlush(pending);

        final Object scheduler = newPaymentReconciliationScheduler();
        final AtomicReference<String> ownerB = new AtomicReference<>();
        doAnswer(invocation -> {
            final PaymentTransaction recovered = (PaymentTransaction) invocation.callRealMethod();
            if (ownerB.get() == null) {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    final var row = paymentReconciliationRepository.findById(captureOperationId).orElseThrow();
                    row.setNextAttemptDate(Instant.EPOCH);
                    row.setClaimUntil(Instant.EPOCH);
                    paymentReconciliationRepository.saveAndFlush(row);
                });
                final Class<?> arbitratorType = Class
                        .forName("com.cp.ecommerce.adapter.persistence.payment.reconciliation.PaymentReconciliationArbitrator");
                final Object arbitrator = applicationContext.getBean(arbitratorType);
                final String nextOwner = ReflectionTestUtils.invokeMethod(arbitrator, "claim", captureOperationId);
                assertThat(nextOwner).isNotNull();
                ownerB.set(nextOwner);
            }
            return recovered;
        }).when(managePaymentInPort)
                .recoverCapturePaymentPendingCompletion(
                        eq(orderNumber),
                        any(BigDecimal.class),
                        any(PaymentMethod.class),
                        any());

        ReflectionTestUtils.invokeMethod(scheduler, "reconcileDueOperations");

        final var takenOver = paymentReconciliationRepository.findById(captureOperationId).orElseThrow();
        assertThat(takenOver.getStatus()).isEqualTo(PaymentReconciliationStatus.PENDING);
        assertThat(takenOver.getClaimId()).isEqualTo(ownerB.get());
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(refundPaymentOutPort, never()).refund(eq(orderNumber), any(), any(), any());

        ReflectionTestUtils.invokeMethod(scheduler, "reconcileClaimed", captureOperationId, ownerB.get());

        assertThat(paymentReconciliationRepository.findById(captureOperationId).orElseThrow().getStatus())
                .isEqualTo(PaymentReconciliationStatus.COMPLETED);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(refundPaymentOutPort, times(1)).refund(eq(orderNumber), any(), any(), any());
        verify(fulfillment, never()).sendMessage(any(Order.class));
    }

    @Test
    void shouldRecoverExpiredPlacementLeaseAfterWorkerDeath() {

        final String sku = "R03B-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            final OutboxEventEntity event = outboxEventEntityRepository
                    .findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)
                    .stream()
                    .filter(candidate -> candidate.getOrderNumber().equals(orderNumber))
                    .findFirst()
                    .orElseThrow();
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setClaimId("dead-worker");
            event.setClaimUntil(Instant.ofEpochMilli(0L));
            outboxEventEntityRepository.save(event);
        });

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);

        newOrchestrator(fulfillment).publishPendingEvents();

        final OutboxEventEntity sent = outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.SENT)
                .stream()
                .filter(event -> event.getOrderNumber().equals(orderNumber))
                .findFirst()
                .orElseThrow();

        assertThat(sent.getClaimId()).isNull();
        assertThat(sent.getClaimUntil()).isNull();
        verify(fulfillment).sendMessage(any(Order.class));
    }

    private void reconcilePaymentsOnce() throws ReflectiveOperationException {

        ReflectionTestUtils.invokeMethod(newPaymentReconciliationScheduler(), "reconcileDueOperations");
    }

    private Object newPaymentReconciliationScheduler() throws ReflectiveOperationException {

        final Class<?> type = Class
                .forName("com.cp.ecommerce.adapter.persistence.payment.reconciliation.PaymentReconciliationScheduler");
        final java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        final Class<?>[] parameterTypes = constructor.getParameterTypes();
        final Object[] dependencies = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            dependencies[index] = applicationContext.getBean(parameterTypes[index]);
        }
        return constructor.newInstance(dependencies);
    }

    private String place(final String sku) {

        catalogProductFixture.ensureActiveProduct(sku, "R03 fixture", BigDecimal.TEN);

        return orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();
    }

    private boolean statusContains(final OutboxEventStatus status, final String orderNumber) {

        return outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(status)
                .stream()
                .anyMatch(event -> event.getOrderNumber().equals(orderNumber));
    }

    private OrderPlacementSagaOrchestrator newOrchestrator(final SendMessageInPort fulfillment) {

        final SagaMetrics metrics = new SagaMetrics(new SimpleMeterRegistry());
        final OrderPlacementBestEffortTail bestEffortTail = new OrderPlacementBestEffortTail(
                mock(OrderPlacementDispatchManager.class),
                mock(ExportOrderInPort.class),
                mock(PublishOrderAuditEventInPort.class),
                mock(PublishOrderAnalyticsEventInPort.class),
                mock(ClassifyOrderRemarksInPort.class),
                mock(DetectDuplicateOrderInPort.class),
                metrics);

        return new OrderPlacementSagaOrchestrator(
                outboxEventEntityRepository,
                manageOrderInPort,
                fulfillment,
                bestEffortTail,
                mock(CancelOrderInPort.class),
                manageStockInPort,
                managePaymentInPort,
                new TransactionTemplate(transactionManager),
                metrics,
                Clock.systemUTC());
    }

    private static String compactUuid() {

        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OrderResource request(final String sku) {

        return new OrderResource(
                "R03 multi-worker outbox claim",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "R03 Buyer",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Claim Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R03 fixture", BigDecimal.TEN, 1, null)),
                PaymentMethod.CARD,
                null);
    }
}
