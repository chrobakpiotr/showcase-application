package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * PostgreSQL-backed R01 counterexample. The scheduled publisher is disabled so the test controls the exact sequence: place ->
 * cancel -> one manual poll.
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

        final String sku = "R01-" + UUID.randomUUID();
        manageStockInPort.receiveStock(sku, 1);

        final String orderNumber = orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();

        assertThat(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .extracting(event -> event.getOrderNumber())
                .contains(orderNumber);
        assertThat(manageOrderInPort.findOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        assertThat(cancelOrderWorkflow.cancelOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.CANCELLED))
                .extracting(event -> event.getOrderNumber())
                .contains(orderNumber);
        assertThat(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .extracting(event -> event.getOrderNumber())
                .doesNotContain(orderNumber);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.PENDING);

        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        final OrderPlacementSagaOrchestrator orchestrator = new OrderPlacementSagaOrchestrator(
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

        orchestrator.publishPendingEvents();

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(fulfillment);
    }

    private static OrderResource request(final String sku) {

        return new OrderResource(
                "R01 cancel-before-first-poll",
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
