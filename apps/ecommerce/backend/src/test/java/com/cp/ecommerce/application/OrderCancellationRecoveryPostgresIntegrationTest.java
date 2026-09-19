package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.order.CancelOrderWorkflow;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;

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
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PostgreSQL-backed proof that durable CANCELLING intent survives a failed compensation attempt.
 */
@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class OrderCancellationRecoveryPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private ManageStockInPort manageStockInPort;

    @MockitoSpyBean
    private SendNotificationInPort sendNotificationInPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;
    @Autowired
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Autowired
    private ManageOrderInPort manageOrderInPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @Autowired
    private OutboxEventEntityRepository outboxEventEntityRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldResumeCancellationFromDurableIntentAfterFirstSideEffectFails() {

        final String sku = "R01X-" + compactUuid();
        catalogProductFixture.ensureActiveProduct(sku, "R01 recovery fixture", BigDecimal.TEN);
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();

        doThrow(new IllegalStateException("inventory unavailable")).doCallRealMethod()
                .when(manageStockInPort)
                .releaseStock(anyString(), eq(sku));

        assertThatThrownBy(() -> cancelOrderWorkflow.cancelOrder(orderNumber)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inventory unavailable");

        assertThat(manageOrderInPort.findOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusContains(OutboxEventStatus.CANCELLING, orderNumber)).isTrue();
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(cancelOrderWorkflow.cancelOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
        assertThat(statusContains(OutboxEventStatus.CANCELLING, orderNumber)).isFalse();
        verify(manageStockInPort, times(2)).releaseStock(anyString(), eq(sku));
        verify(sendNotificationInPort, times(1)).sendNotification(
                anyString(),
                eq(NotificationType.ORDER_CANCELLED),
                eq("Order " + orderNumber + " cancelled"),
                eq("Your order " + orderNumber + " was cancelled."));
    }

    private boolean statusContains(final OutboxEventStatus status, final String orderNumber) {

        return outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(status)
                .stream()
                .anyMatch(event -> event.getOrderNumber().equals(orderNumber));
    }

    private static String compactUuid() {

        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OrderResource request(final String sku) {

        return new OrderResource(
                "R01 cancellation recovery",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "R01 Recovery Buyer",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R01 recovery fixture", BigDecimal.TEN, 1, null)),
                PaymentMethod.CARD,
                null);
    }
}
