package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.adapter.persistence.order.outbox.OrderPlacementSagaOrchestrator;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.incoming.GetStockLevelInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=true",
                "outbox.publisher.poll-interval-ms=3600000",
                "outbox.publisher.max-fulfillment-attempts=1",
                "notification.retry.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000" })
class SagaCompensationRecoveryPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoBean
    private SendMessageInPort sendMessageInPort;

    @MockitoSpyBean
    private ManageStockInPort manageStockInPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;
    @Autowired
    private OrderPlacementSagaOrchestrator orchestrator;

    @Autowired
    private ManageOrderInPort manageOrderInPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @Autowired
    private GetStockLevelInPort getStockLevelInPort;

    @Autowired
    private OutboxEventEntityRepository outboxEventEntityRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldResumeDurableCompensationAfterStockReleaseFailure() {

        final String sku = "R07-" + compactUuid();
        catalogProductFixture.ensureActiveProduct(sku, "R07 fixture", BigDecimal.TEN);
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = orderController.placeOrder(request(sku), UUID.randomUUID().toString()).orderNumber();

        doThrow(new IllegalStateException("fulfillment unavailable")).when(sendMessageInPort).sendMessage(any(Order.class));
        doThrow(new IllegalStateException("inventory unavailable")).doCallRealMethod()
                .when(manageStockInPort)
                .releaseStock(anyString(), eq(sku));

        orchestrator.publishPendingEvents();

        assertThat(manageOrderInPort.findOrder(orderNumber).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusContains(OutboxEventStatus.COMPENSATING, orderNumber)).isTrue();
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityReserved()).isEqualTo(1);

        orchestrator.publishPendingEvents();

        final StockLevel stock = getStockLevelInPort.getStockLevel(sku);
        assertThat(statusContains(OutboxEventStatus.COMPENSATED, orderNumber)).isTrue();
        assertThat(statusContains(OutboxEventStatus.COMPENSATING, orderNumber)).isFalse();
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(stock.getQuantityReserved()).isZero();
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
                "R07 durable compensation",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "R07 Buyer",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Recovery Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R07 fixture", BigDecimal.TEN, 1, null)),
                PaymentMethod.CARD,
                null);
    }
}
