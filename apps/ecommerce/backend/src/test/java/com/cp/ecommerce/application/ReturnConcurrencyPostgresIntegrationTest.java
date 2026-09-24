package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.cp.ecommerce.domain.returns.ReturnRequestCommand;
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
    void shouldRestoreExactRejectedMinorUnitAcrossABRejectACD() {

        final String sku = shortSku("Q05R");
        final String orderNumber = placeOrder(sku, 3);
        final BigDecimal entitlement = new BigDecimal("0.01");

        final ReturnRequest first = requestFromLineEntitlement(orderNumber, sku, 3, entitlement);
        final ReturnRequest second = requestFromLineEntitlement(orderNumber, sku, 3, entitlement);
        returnModerationInPort.rejectReturn(first.getReturnNumber());
        final ReturnRequest third = requestFromLineEntitlement(orderNumber, sku, 3, entitlement);
        final ReturnRequest fourth = requestFromLineEntitlement(orderNumber, sku, 3, entitlement);

        assertThat(first.getRefundAmount()).isEqualByComparingTo("0.01");
        assertThat(second.getRefundAmount()).isEqualByComparingTo("0.00");
        assertThat(third.getRefundAmount()).isEqualByComparingTo("0.01");
        assertThat(fourth.getRefundAmount()).isEqualByComparingTo("0.00");

        final List<ReturnRequest> active = listReturnsInPort.listReturnsForOrder(orderNumber)
                .stream()
                .filter(candidate -> candidate.getStatus() != ReturnStatus.REJECTED)
                .toList();
        assertThat(active.stream().mapToInt(ReturnRequest::getQuantity).sum()).isEqualTo(3);
        assertThat(active.stream().map(ReturnRequest::getRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(entitlement);
    }

    @Test
    void shouldRespectHistoricalPersistedRefundSnapshotWhenAllocatingRemainingEntitlement() {

        final String sku = shortSku("Q05H");
        final String orderNumber = placeOrder(sku, 2);
        requestReturnInPort
                .requestReturn(new ReturnRequestCommand(orderNumber, sku, 1, 2, "Q05 historical snapshot", BigDecimal.ZERO));

        final ReturnRequest remaining = requestFromLineEntitlement(orderNumber, sku, 2, new BigDecimal("0.01"));

        assertThat(remaining.getRefundAmount()).isEqualByComparingTo("0.01");
    }

    @Test
    void shouldConserveRefundEntitlementAcrossGeneratedRejectPatterns() {

        final int[] quantities = { 2, 3, 4 };
        final int[] entitlementCents = { 1, 2, 5 };

        for (final int quantity : quantities) {
            for (final int cents : entitlementCents) {
                final String sku = shortSku("Q05G");
                final String orderNumber = placeOrder(sku, quantity);
                final BigDecimal entitlement = BigDecimal.valueOf(cents, 2);

                final ReturnRequest rejected = requestFromLineEntitlement(orderNumber, sku, quantity, entitlement);
                returnModerationInPort.rejectReturn(rejected.getReturnNumber());

                for (int unit = 0; unit < quantity; unit++) {
                    requestFromLineEntitlement(orderNumber, sku, quantity, entitlement);
                }

                final List<ReturnRequest> active = listReturnsInPort.listReturnsForOrder(orderNumber)
                        .stream()
                        .filter(candidate -> candidate.getStatus() != ReturnStatus.REJECTED)
                        .toList();

                assertThat(active.stream().mapToInt(ReturnRequest::getQuantity).sum()).isEqualTo(quantity);
                assertThat(active.stream().map(ReturnRequest::getRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                        .isEqualByComparingTo(entitlement);
            }
        }
    }

    @Test
    void shouldPreserveHistoricalActiveAmountAndAllocateOnlyRemainingMinorUnits() {

        final String sku = shortSku("Q07H");
        final String orderNumber = placeOrder(sku, 3);
        final BigDecimal entitlement = new BigDecimal("0.05");

        final ReturnRequest historical = requestReturnInPort.requestReturn(
                new ReturnRequestCommand(orderNumber, sku, 1, 3, "historical persisted amount", new BigDecimal("0.01")));
        final ReturnRequest remaining = requestFromLineEntitlement(orderNumber, sku, 2, 3, entitlement);

        assertThat(historical.getRefundAmount()).isEqualByComparingTo("0.01");
        assertThat(remaining.getRefundAmount()).isEqualByComparingTo("0.04");
        assertIndependentLedgerModel(orderNumber, 3, entitlement);
    }

    @Test
    void shouldReleaseRejectedAmountAcrossMixedActiveStates() {

        final String sku = shortSku("Q07M");
        final String orderNumber = placeOrder(sku, 4);
        final BigDecimal entitlement = new BigDecimal("0.05");

        final ReturnRequest first = requestFromLineEntitlement(orderNumber, sku, 1, 4, entitlement);
        final ReturnRequest second = requestFromLineEntitlement(orderNumber, sku, 1, 4, entitlement);
        final ReturnRequest third = requestFromLineEntitlement(orderNumber, sku, 1, 4, entitlement);

        returnModerationInPort.approveReturn(first.getReturnNumber());
        returnModerationInPort.markRefunded(first.getReturnNumber());
        returnModerationInPort.approveReturn(third.getReturnNumber());
        final ReturnRequest rejected = returnModerationInPort.rejectReturn(second.getReturnNumber());

        final ReturnRequest replacement = requestFromLineEntitlement(orderNumber, sku, 1, 4, entitlement);
        final ReturnRequest finalUnit = requestFromLineEntitlement(orderNumber, sku, 1, 4, entitlement);

        assertThat(rejected.getRefundAmount()).isEqualByComparingTo("0.01");
        assertThat(replacement.getRefundAmount()).isEqualByComparingTo(rejected.getRefundAmount());
        assertThat(finalUnit.getRefundAmount()).isEqualByComparingTo("0.01");
        assertIndependentLedgerModel(orderNumber, 4, entitlement);
    }

    @Test
    void shouldConserveMinorUnitsAcrossTwoConcurrentEntitlementRequests() throws Exception {

        final String sku = shortSku("Q07C");
        final String orderNumber = placeOrder(sku, 2);
        final BigDecimal entitlement = new BigDecimal("0.01");
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<ReturnRequest> first = executor
                    .submit(() -> requestEntitlementAfterBarrier(orderNumber, sku, 2, entitlement, ready, start));
            final Future<ReturnRequest> second = executor
                    .submit(() -> requestEntitlementAfterBarrier(orderNumber, sku, 2, entitlement, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final ReturnRequest firstResult = first.get(10, TimeUnit.SECONDS);
            final ReturnRequest secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.getRefundAmount().add(secondResult.getRefundAmount())).isEqualByComparingTo(entitlement);
            assertThat(List.of(firstResult.getRefundAmount(), secondResult.getRefundAmount()))
                    .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.01"))
                    .anySatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.00"));
        }

        assertIndependentLedgerModel(orderNumber, 2, entitlement);
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

        return requestReturnInPort
                .requestReturn(new ReturnRequestCommand(orderNumber, sku, 1, 1, "R05 concurrent return", UNIT_PRICE));
    }

    private ReturnRequest requestFromLineEntitlement(
            final String orderNumber,
            final String sku,
            final int orderedQuantity,
            final BigDecimal fullLineEntitlement) {

        return requestFromLineEntitlement(orderNumber, sku, 1, orderedQuantity, fullLineEntitlement);
    }

    private ReturnRequest requestFromLineEntitlement(
            final String orderNumber,
            final String sku,
            final int requestedQuantity,
            final int orderedQuantity,
            final BigDecimal fullLineEntitlement) {

        return requestReturnInPort.requestReturnFromLineEntitlement(
                new ReturnRequestCommand(
                        orderNumber,
                        sku,
                        requestedQuantity,
                        orderedQuantity,
                        "Q05/Q07 entitlement conservation",
                        fullLineEntitlement));
    }

    private ReturnRequest requestEntitlementAfterBarrier(
            final String orderNumber,
            final String sku,
            final int orderedQuantity,
            final BigDecimal fullLineEntitlement,
            final CountDownLatch ready,
            final CountDownLatch start) {

        awaitBarrier(ready, start);
        return requestFromLineEntitlement(orderNumber, sku, 1, orderedQuantity, fullLineEntitlement);
    }

    private void assertIndependentLedgerModel(
            final String orderNumber,
            final int orderedQuantity,
            final BigDecimal fullLineEntitlement) {

        final List<ReturnRequest> active = listReturnsInPort.listReturnsForOrder(orderNumber)
                .stream()
                .filter(candidate -> candidate.getStatus() != ReturnStatus.REJECTED)
                .toList();
        final int activeQuantity = active.stream().mapToInt(ReturnRequest::getQuantity).sum();
        final BigDecimal activeAmount = active.stream()
                .map(ReturnRequest::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(activeQuantity).isLessThanOrEqualTo(orderedQuantity);
        assertThat(activeAmount).isLessThanOrEqualTo(fullLineEntitlement);
        if (activeQuantity == orderedQuantity) {
            assertThat(activeAmount).isEqualByComparingTo(fullLineEntitlement);
        }
    }

    private String placeOrder(final String sku, final int quantity) {

        catalogProductFixture.ensureActiveProduct(sku, "R05 fixture", UNIT_PRICE);

        manageStockInPort.receiveStock(sku, quantity);
        return orderController.placeOrder(orderRequest(sku, quantity), UUID.randomUUID().toString()).orderNumber();
    }

    private static OrderResource orderRequest(final String sku, final int quantity) {

        return new OrderResource(
                "R05 return concurrency",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
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
