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

import com.cp.ecommerce.adapter.web.order.OrderController;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ListReturnsInPort;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.foundation.exception.ReturnQuantityConflictException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRejectableException;

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

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "notification.retry.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000" })
class ReturnConcurrencyPostgresIntegrationTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("30.00");

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
    private ManageStockInPort manageStockInPort;

    @Autowired
    private RequestReturnInPort requestReturnInPort;

    @Autowired
    private ReturnModerationInPort returnModerationInPort;

    @Autowired
    private GetReturnInPort getReturnInPort;

    @Autowired
    private ListReturnsInPort listReturnsInPort;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldAllowOnlyOneConcurrentRequestForLastReturnableUnit() throws Exception {

        final String sku = shortSku("R05A");
        final String orderNumber = placeOrder(sku, 1);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            final Future<ReturnRequest> first = executor.submit(() -> requestAfterBarrier(orderNumber, sku, ready, start));
            final Future<ReturnRequest> second = executor.submit(() -> requestAfterBarrier(orderNumber, sku, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final int successes = successfulRequest(first) + successfulRequest(second);
            assertThat(successes).isEqualTo(1);
        }

        assertThat(listReturnsInPort.listReturnsForOrder(orderNumber)).hasSize(1);
    }

    @Test
    void shouldReleaseRejectedEntitlementExactlyOnce() {

        final String sku = shortSku("R05B");
        final String orderNumber = placeOrder(sku, 1);
        final ReturnRequest first = request(orderNumber, sku);

        final ReturnRequest rejected = returnModerationInPort.rejectReturn(first.getReturnNumber());
        final ReturnRequest replayedReject = returnModerationInPort.rejectReturn(first.getReturnNumber());

        assertThat(rejected.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(replayedReject.getStatus()).isEqualTo(ReturnStatus.REJECTED);

        final ReturnRequest replacement = request(orderNumber, sku);
        assertThat(replacement.getStatus()).isEqualTo(ReturnStatus.REQUESTED);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> request(orderNumber, sku))
                .isInstanceOf(ReturnQuantityConflictException.class);
    }

    @Test
    void shouldAllowOnlyOneConcurrentApproveOrRejectDecision() throws Exception {

        final String sku = shortSku("R05C");
        final String orderNumber = placeOrder(sku, 1);
        final ReturnRequest requested = request(orderNumber, sku);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            final Future<ReturnRequest> approve = executor
                    .submit(() -> moderateAfterBarrier(requested.getReturnNumber(), true, ready, start));
            final Future<ReturnRequest> reject = executor
                    .submit(() -> moderateAfterBarrier(requested.getReturnNumber(), false, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final int successes = successfulModeration(approve) + successfulModeration(reject);
            assertThat(successes).isEqualTo(1);
        }

        final ReturnRequest finalState = getReturnInPort.getReturn(requested.getReturnNumber());
        assertThat(finalState.getStatus()).isIn(ReturnStatus.APPROVED, ReturnStatus.REJECTED);
    }

    private ReturnRequest requestAfterBarrier(
            final String orderNumber,
            final String sku,
            final CountDownLatch ready,
            final CountDownLatch start) {

        awaitBarrier(ready, start);
        return request(orderNumber, sku);
    }

    private ReturnRequest moderateAfterBarrier(
            final String returnNumber,
            final boolean approve,
            final CountDownLatch ready,
            final CountDownLatch start) {

        awaitBarrier(ready, start);
        return approve ? returnModerationInPort.approveReturn(returnNumber) : returnModerationInPort.rejectReturn(returnNumber);
    }

    private static void awaitBarrier(final CountDownLatch ready, final CountDownLatch start) {

        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {

                throw new IllegalStateException("Timed out waiting for R05 concurrency barrier");
            }
        } catch (final InterruptedException exception) {

            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for R05 concurrency barrier", exception);
        }
    }

    private static int successfulRequest(final Future<ReturnRequest> future) throws Exception {

        try {
            future.get(10, TimeUnit.SECONDS);
            return 1;
        } catch (final ExecutionException exception) {

            assertThat(exception.getCause()).isInstanceOf(ReturnQuantityConflictException.class);
            return 0;
        }
    }

    private static int successfulModeration(final Future<ReturnRequest> future) throws Exception {

        try {
            future.get(10, TimeUnit.SECONDS);
            return 1;
        } catch (final ExecutionException exception) {

            assertThat(exception.getCause())
                    .isInstanceOfAny(ReturnRequestNotApprovableException.class, ReturnRequestNotRejectableException.class);
            return 0;
        }
    }

    private ReturnRequest request(final String orderNumber, final String sku) {

        return requestReturnInPort.requestReturn(orderNumber, sku, 1, 1, "R05 concurrent return", UNIT_PRICE);
    }

    private String placeOrder(final String sku, final int quantity) {

        catalogProductFixture.ensureActiveProduct(sku, "R05 fixture", UNIT_PRICE);

        manageStockInPort.receiveStock(sku, quantity);
        return orderController.placeOrder(orderRequest(sku, quantity), UUID.randomUUID().toString()).orderNumber();
    }

    private static OrderResource orderRequest(final String sku, final int quantity) {

        return new OrderResource(
                "R05 return concurrency",
                new Date(),
                new CustomerResource(
                        "R05 Buyer",
                        compactUuid() + "@example.com",
                        "123",
                        "Concurrency Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R05 fixture", UNIT_PRICE, quantity, null)),
                PaymentMethod.CARD,
                null);
    }

    private static String shortSku(final String prefix) {

        return prefix + "-" + compactUuid();
    }

    private static String compactUuid() {

        return UUID.randomUUID().toString().replace("-", "");
    }

}
