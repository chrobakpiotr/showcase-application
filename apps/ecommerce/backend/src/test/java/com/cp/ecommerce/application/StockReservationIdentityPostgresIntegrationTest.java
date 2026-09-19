package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventStatus;
import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.application.order.CancelOrderWorkflow;
import com.cp.ecommerce.domain.inventory.port.incoming.GetStockLevelInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * PostgreSQL-backed R02 counterexample and acceptance tests.
 */
@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class StockReservationIdentityPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private SendNotificationInPort sendNotificationInPort;

    @Autowired
    private OrderController orderController;

    @Autowired
    private CatalogProductFixture catalogProductFixture;
    @Autowired
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private GetStockLevelInPort getStockLevelInPort;

    @Autowired
    private ManageOrderInPort manageOrderInPort;

    @Autowired
    private OutboxEventEntityRepository outboxEventEntityRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldNotReleaseOtherOrdersReservationWhenCancellationIsRetried() {

        final String sku = "R02-" + compactUuid();
        manageStockInPort.receiveStock(sku, 10);

        final AtomicBoolean failFirstCancellationNotification = new AtomicBoolean(true);
        doAnswer(invocation -> {
            final NotificationType type = invocation.getArgument(1);
            if (type == NotificationType.ORDER_CANCELLED && failFirstCancellationNotification.compareAndSet(true, false)) {

                throw new IllegalStateException("notification unavailable after stock release");
            }
            return null;
        }).when(sendNotificationInPort).sendNotification(anyString(), any(NotificationType.class), anyString(), anyString());

        final String orderA = place(sku, 3);
        final String orderB = place(sku, 4);

        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityReserved()).isEqualTo(7);

        assertThatThrownBy(() -> cancelOrderWorkflow.cancelOrder(orderA)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notification unavailable");
        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityReserved()).isEqualTo(4);
        assertThat(statusContains(OutboxEventStatus.CANCELLING, orderA)).isTrue();

        assertThat(cancelOrderWorkflow.cancelOrder(orderA).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityReserved()).isEqualTo(4);
        assertThat(manageOrderInPort.findOrder(orderB).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldReserveOnceAndReleaseOnceUnderConcurrentDuplicateReplay() throws Exception {

        final String sku = "R02C-" + compactUuid();
        final String reservationA = UUID.randomUUID().toString();
        final String reservationB = UUID.randomUUID().toString();
        manageStockInPort.receiveStock(sku, 10);

        manageStockInPort.reserveStock(reservationA, sku, 3);
        manageStockInPort.reserveStock(reservationA, sku, 3);
        manageStockInPort.reserveStock(reservationB, sku, 4);

        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityReserved()).isEqualTo(7);

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            final Future<?> first = executor.submit(() -> releaseAfterBarrier(reservationA, sku, ready, start));
            final Future<?> second = executor.submit(() -> releaseAfterBarrier(reservationA, sku, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityReserved()).isEqualTo(4);
    }

    private void releaseAfterBarrier(
            final String reservationId,
            final String sku,
            final CountDownLatch ready,
            final CountDownLatch start) {

        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {

                throw new IllegalStateException("Timed out waiting for duplicate-release barrier");
            }
        } catch (final InterruptedException exception) {

            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for duplicate-release barrier", exception);
        }
        manageStockInPort.releaseStock(reservationId, sku);
    }

    private String place(final String sku, final int quantity) {

        catalogProductFixture.ensureActiveProduct(sku, "R02 fixture", BigDecimal.TEN);

        return orderController.placeOrder(request(sku, quantity), UUID.randomUUID().toString()).orderNumber();
    }

    private boolean statusContains(final OutboxEventStatus status, final String orderNumber) {

        return outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(status)
                .stream()
                .anyMatch(event -> event.getOrderNumber().equals(orderNumber));
    }

    private static String compactUuid() {

        return UUID.randomUUID().toString().replace("-", "");
    }

    private static OrderResource request(final String sku, final int quantity) {

        return new OrderResource(
                "R02 reservation identity",
                new Date(),
                new CustomerResource(
                        "R02 Buyer",
                        UUID.randomUUID() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R02 fixture", BigDecimal.TEN, quantity, null)),
                PaymentMethod.CARD,
                null);
    }
}
