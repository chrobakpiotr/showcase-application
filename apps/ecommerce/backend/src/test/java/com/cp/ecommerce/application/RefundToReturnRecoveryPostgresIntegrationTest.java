package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.returns.ReturnStateNotificationTransaction;
import com.cp.ecommerce.application.returns.ReturnWorkflow;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationEventKey;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=true",
                "payment.reconciliation.poll-interval-ms=600000",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class RefundToReturnRecoveryPostgresIntegrationTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("30.00");
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("60.00");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;

    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @Autowired
    private ReturnWorkflow returnWorkflow;

    @Autowired
    private GetReturnInPort getReturnInPort;

    @Autowired
    private NotificationEntityRepository notificationEntityRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void completedRefundMustResumeApprovedReturnWithoutSecondUserAction() throws Exception {

        final String sku = "B03B-" + compactUuid();
        final String orderNumber = capturedOrder(sku);
        final ReturnRequest requested = returnWorkflow.requestReturn(orderNumber, sku, 1, "B03b durable refund continuation");

        doThrow(new IllegalStateException("simulated crash after durable refund")).doCallRealMethod()
                .when(returnStateNotificationTransaction)
                .markRefundedAndNotify(requested.getReturnNumber());

        assertThatThrownBy(() -> returnWorkflow.approveReturn(requested.getReturnNumber()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated crash");

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus())
                .as("provider/local payment refund must already be durable before simulated crash")
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        assertThat(getReturnInPort.getReturn(requested.getReturnNumber()).getStatus())
                .as("precondition: crash leaves RMA APPROVED after durable refund completion")
                .isEqualTo(ReturnStatus.APPROVED);

        runExistingPaymentReconciliation();
        runExistingPaymentReconciliation();

        final ReturnRequest recovered = getReturnInPort.getReturn(requested.getReturnNumber());
        assertThat(recovered.getStatus()).as("payment recovery must continue the linked RMA without another user click")
                .isEqualTo(ReturnStatus.REFUNDED);

        final String eventKey = NotificationEventKey
                .of("return", requested.getReturnNumber(), NotificationType.RETURN_REFUNDED, "refunded-v1");

        assertThat(
                notificationEntityRepository.findAll()
                        .stream()
                        .filter(notification -> eventKey.equals(notification.getEventKey()))
                        .count())
                .as("recovery replay must enqueue exactly one RETURN_REFUNDED event")
                .isEqualTo(1L);
    }

    private void runExistingPaymentReconciliation() throws Exception {

        final Object scheduler = applicationContext.getBean("paymentReconciliationScheduler");
        final var method = scheduler.getClass().getDeclaredMethod("reconcileDueOperations");
        method.setAccessible(true);
        method.invoke(scheduler);
    }

    private String capturedOrder(final String sku) {

        catalogProductFixture.ensureActiveProduct(sku, "B03b fixture", UNIT_PRICE);
        manageStockInPort.receiveStock(sku, 2);
        final String orderNumber = orderController.placeOrder(orderRequest(sku), UUID.randomUUID().toString()).orderNumber();
        managePaymentInPort.capturePayment(orderNumber, ORDER_TOTAL, PaymentMethod.CARD);
        return orderNumber;
    }

    private static OrderResource orderRequest(final String sku) {

        return new OrderResource(
                "B03b refund recovery",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "B03b Buyer",
                        compactUuid() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "B03b fixture", UNIT_PRICE, 2, null)),
                PaymentMethod.CARD,
                null);
    }

    private static String compactUuid() {

        return UUID.randomUUID().toString().replace("-", "");
    }
}
