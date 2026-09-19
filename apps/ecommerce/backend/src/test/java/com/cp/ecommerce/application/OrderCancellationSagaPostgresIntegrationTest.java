package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cp.ecommerce.adapter.persistence.order.outbox.OrderPlacementSagaOrchestrator;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.order.CancelOrderWorkflow;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
import com.cp.ecommerce.domain.order.port.incoming.DetectDuplicateOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.foundation.exception.OrderNotCancellableException;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * PostgreSQL-backed R01 acceptance tests for cancellation/placement-saga arbitration.
 */
@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class OrderCancellationSagaPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;
    @Autowired
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private ManageOrderInPort manageOrderInPort;

    @Autowired
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

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
    void shouldNotCaptureOrFulfillWhenOrderWasCancelledBeforeFirstPoll() {

        final String sku = "R01A-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);

        final String orderNumber = place(sku);

        assertThat(cancelOrderWorkflow.cancelOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
        assertThat(statusContains(OutboxEventStatus.PENDING, orderNumber)).isFalse();
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.PENDING);

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        newOrchestrator(fulfillment).publishPendingEvents();

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(fulfillment);
    }

    @Test
    void shouldNotRecaptureAfterFulfillmentRetryWasCancelledAndRefunded() {

        final String sku = "R01B-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        doThrow(new IllegalStateException("fulfillment unavailable")).when(fulfillment).sendMessage(any(Order.class));
        final OrderPlacementSagaOrchestrator orchestrator = newOrchestrator(fulfillment);

        orchestrator.publishPendingEvents();

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(statusContains(OutboxEventStatus.PENDING, orderNumber)).isTrue();

        assertThat(cancelOrderWorkflow.cancelOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();

        orchestrator.publishPendingEvents();

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(fulfillment, times(1)).sendMessage(any(Order.class));
    }

    @Test
    void shouldLetPollWinnerFinishAndRejectConcurrentCancellation() throws Exception {

        final String sku = "R01R-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);

        final CountDownLatch fulfillmentEntered = new CountDownLatch(1);
        final CountDownLatch releaseFulfillment = new CountDownLatch(1);
        final CountDownLatch cancellationStarted = new CountDownLatch(1);
        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        doAnswer(invocation -> {
            fulfillmentEntered.countDown();
            if (!releaseFulfillment.await(10, TimeUnit.SECONDS)) {

                throw new IllegalStateException("Timed out waiting to release fulfillment");
            }
            return null;
        }).when(fulfillment).sendMessage(any(Order.class));

        final OrderPlacementSagaOrchestrator orchestrator = newOrchestrator(fulfillment);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            final Future<?> poll = executor.submit(orchestrator::publishPendingEvents);
            assertThat(fulfillmentEntered.await(10, TimeUnit.SECONDS)).isTrue();

            final Future<Order> cancellation = executor.submit(() -> {
                cancellationStarted.countDown();
                return cancelOrderWorkflow.cancelOrder(orderNumber);
            });
            assertThat(cancellationStarted.await(10, TimeUnit.SECONDS)).isTrue();

            releaseFulfillment.countDown();
            poll.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> cancellation.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OrderNotCancellableException.class);
        }

        assertThat(statusContains(OutboxEventStatus.SENT, orderNumber)).isTrue();
        assertThat(manageOrderInPort.findOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    private String place(final String sku) {

        catalogProductFixture.ensureActiveProduct(sku, "R01 fixture", BigDecimal.TEN);

        return orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();
    }

    private boolean statusContains(final OutboxEventStatus status, final String orderNumber) {

        return outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(status)
                .stream()
                .anyMatch(event -> event.getOrderNumber().equals(orderNumber));
    }

    private OrderPlacementSagaOrchestrator newOrchestrator(final SendMessageInPort fulfillment) {

        return new OrderPlacementSagaOrchestrator(
                outboxEventEntityRepository,
                manageOrderInPort,
                fulfillment,
                mock(SendOrderConfirmationEmailInPort.class),
                mock(ExportOrderInPort.class),
                mock(PublishOrderAuditEventInPort.class),
                mock(PublishOrderAnalyticsEventInPort.class),
                mock(RouteOrderNotificationInPort.class),
                mock(ClassifyOrderRemarksInPort.class),
                mock(DetectDuplicateOrderInPort.class),
                mock(CancelOrderInPort.class),
                manageStockInPort,
                managePaymentInPort,
                new TransactionTemplate(transactionManager),
                new SagaMetrics(new SimpleMeterRegistry()));
    }

    private static String compactUuid() {

        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OrderResource request(final String sku) {

        return new OrderResource(
                "R01 cancellation arbitration",
                new Date(),
                new CustomerResource(
                        "R01 Buyer",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R01 fixture", BigDecimal.TEN, 1, null)),
                PaymentMethod.CARD,
                null);
    }
}
