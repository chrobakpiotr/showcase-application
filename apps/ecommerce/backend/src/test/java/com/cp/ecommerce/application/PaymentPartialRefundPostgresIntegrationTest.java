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
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=1000",
                "resilience4j.ratelimiter.instances.cancelOrder.limit-for-period=1000" })
class PaymentPartialRefundPostgresIntegrationTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("30.00");
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("60.00");
    private static final BigDecimal CONCURRENT_REFUND = new BigDecimal("40.00");

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
    private RequestReturnInPort requestReturnInPort;

    @Autowired
    private ReturnModerationInPort returnModerationInPort;

    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldRefundTwoReturnsIncrementallyAndReplayApprovalIdempotently() {

        final String sku = shortSku("R04A");
        final String orderNumber = capturedOrder(sku);

        final String firstReturn = requestReturn(orderNumber, sku, 1);
        approveAndRefund(firstReturn);

        PaymentTransaction payment = getPaymentInPort.getPayment(orderNumber);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(UNIT_PRICE);

        approveAndRefund(firstReturn);
        payment = getPaymentInPort.getPayment(orderNumber);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(UNIT_PRICE);

        final String secondReturn = requestReturn(orderNumber, sku, 1);
        approveAndRefund(secondReturn);

        payment = getPaymentInPort.getPayment(orderNumber);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(ORDER_TOTAL);
        assertThat(payment.getRemainingRefundableAmount()).isZero();
    }

    @Test
    void shouldRefundOnlyRemainingAmountOnWholeOrderRefundAfterPartialReturn() {

        final String sku = shortSku("R04B");
        final String orderNumber = capturedOrder(sku);
        final String partialReturn = requestReturn(orderNumber, sku, 1);
        approveAndRefund(partialReturn);

        final PaymentTransaction result = managePaymentInPort.refundPayment(orderNumber);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getRefundedAmount()).isEqualByComparingTo(ORDER_TOTAL);
    }

    @Test
    void shouldPreventConcurrentClaimsFromOverRefundingPayment() throws Exception {

        final String sku = shortSku("R04C");
        final String orderNumber = capturedOrder(sku);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            final Future<PaymentTransaction> first = executor
                    .submit(() -> refundAfterBarrier(orderNumber, "R04-REF-A-" + compactUuid(), ready, start));
            final Future<PaymentTransaction> second = executor
                    .submit(() -> refundAfterBarrier(orderNumber, "R04-REF-B-" + compactUuid(), ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final int successes = successful(first) + successful(second);
            assertThat(successes).isEqualTo(1);
        }

        final PaymentTransaction payment = getPaymentInPort.getPayment(orderNumber);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(CONCURRENT_REFUND);
    }

    private PaymentTransaction refundAfterBarrier(
            final String orderNumber,
            final String refundId,
            final CountDownLatch ready,
            final CountDownLatch start) {

        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {

                throw new IllegalStateException("Timed out waiting for refund barrier");
            }
        } catch (final InterruptedException exception) {

            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for refund barrier", exception);
        }
        return managePaymentInPort.refundPayment(orderNumber, refundId, CONCURRENT_REFUND);
    }

    private static int successful(final Future<PaymentTransaction> future) throws Exception {

        try {
            future.get(10, TimeUnit.SECONDS);
            return 1;
        } catch (final ExecutionException exception) {

            assertThatThrownBy(() -> {
                throw exception.getCause();
            }).isInstanceOf(PaymentRefundConflictException.class);
            return 0;
        }
    }

    private String capturedOrder(final String sku) {

        catalogProductFixture.ensureActiveProduct(sku, "R04 fixture", UNIT_PRICE);

        manageStockInPort.receiveStock(sku, 2);
        final String orderNumber = orderController.placeOrder(orderRequest(sku), UUID.randomUUID().toString()).orderNumber();
        managePaymentInPort.capturePayment(orderNumber, ORDER_TOTAL, PaymentMethod.CARD);
        return orderNumber;
    }

    private String requestReturn(final String orderNumber, final String sku, final int quantity) {

        final ReturnRequest created = requestReturnInPort.requestReturn(
                orderNumber,
                sku,
                quantity,
                2,
                "R04 partial return",
                UNIT_PRICE.multiply(BigDecimal.valueOf(quantity)));
        return created.getReturnNumber();
    }

    private void approveAndRefund(final String returnNumber) {

        final ReturnRequest approved = returnModerationInPort.approveReturn(returnNumber);
        if (approved == null) {

            throw new IllegalStateException("Return disappeared before approval: " + returnNumber);
        }
        managePaymentInPort.refundPayment(approved.getOrderNumber(), approved.getReturnNumber(), approved.getRefundAmount());
        returnModerationInPort.markRefunded(returnNumber);
    }

    private static OrderResource orderRequest(final String sku) {

        return new OrderResource(
                "R04 partial refunds",
                Instant.ofEpochMilli(Instant.now().toEpochMilli()),
                new CustomerResource(
                        "R04 Buyer",
                        compactUuid() + "@example.com",
                        "123",
                        "Test Street 1",
                        "00-001",
                        "Warsaw",
                        "PL"),
                List.of(new OrderLineItemResource(sku, "R04 fixture", UNIT_PRICE, 2, null)),
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
