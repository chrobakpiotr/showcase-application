package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = { "outbox.publisher.enabled=false", "payment.reconciliation.enabled=false" })
class PaymentOperationPreparationPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private ChargePaymentOutPort chargePaymentOutPort;

    @MockitoSpyBean
    private ManagePaymentReconciliationOutPort reconciliationOutPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @Autowired
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PreparePaymentProviderOperationOutPort preparePaymentProviderOperationOutPort;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void stalePendingPrepareMustNotOverwriteFullyRefundedCanonicalPayment() throws Exception {

        final String orderNumber = "B02-" + UUID.randomUUID().toString().substring(0, 20);
        final String operationId = "ORDER-CAPTURE:" + orderNumber;
        final BigDecimal amount = new BigDecimal("10.00");

        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(orderNumber)
                .amount(amount)
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .created(Instant.now())
                .build();

        preparePaymentProviderOperationOutPort.prepareCapture(operationId, pending);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.PENDING);

        final CountDownLatch staleSnapshotRead = new CountDownLatch(1);
        final CountDownLatch canonicalMutationFinished = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var staleWorker = executor.submit(() -> {
                final PaymentTransaction stale = getPaymentInPort.getPayment(orderNumber);
                assertThat(stale.getStatus()).isEqualTo(PaymentStatus.PENDING);
                staleSnapshotRead.countDown();

                if (!canonicalMutationFinished.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for canonical payment mutation");
                }

                preparePaymentProviderOperationOutPort.prepareCapture(operationId, stale);
                return null;
            });

            final var canonicalWorker = executor.submit(() -> {
                if (!staleSnapshotRead.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for stale snapshot read");
                }

                final PaymentTransaction captured = managePaymentInPort.capturePayment(orderNumber, amount, PaymentMethod.CARD);
                assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

                final PaymentTransaction refunded = managePaymentInPort.refundPayment(orderNumber);
                assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
                canonicalMutationFinished.countDown();
                return refunded;
            });

            canonicalWorker.get(30, TimeUnit.SECONDS);
            staleWorker.get(30, TimeUnit.SECONDS);
        } finally {
            canonicalMutationFinished.countDown();
            staleSnapshotRead.countDown();
        }

        final PaymentTransaction persisted = getPaymentInPort.getPayment(orderNumber);

        assertThat(persisted.getStatus())
                .as("stale PENDING prepare must never overwrite a newer fully REFUNDED canonical payment")
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(persisted.getRefundedAmount()).isEqualByComparingTo(amount);
        assertThat(persisted.getGatewayReference()).isNotBlank();
    }

    @Test
    void stalePendingPrepareMustNotOverwritePartiallyRefundedCanonicalPayment() {

        final String orderNumber = "B02-P-" + UUID.randomUUID().toString().substring(0, 18);
        final String operationId = "ORDER-CAPTURE:" + orderNumber;
        final BigDecimal amount = new BigDecimal("10.00");
        final BigDecimal partial = new BigDecimal("4.00");
        final PaymentTransaction stale = PaymentTransaction.builder()
                .orderNumber(orderNumber)
                .amount(amount)
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .created(Instant.now())
                .build();

        preparePaymentProviderOperationOutPort.prepareCapture(operationId, stale);
        assertThat(managePaymentInPort.capturePayment(orderNumber, amount, PaymentMethod.CARD).getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
        final PaymentTransaction partiallyRefunded = managePaymentInPort
                .refundPayment(orderNumber, "B02-REFUND:" + orderNumber, partial);
        assertThat(partiallyRefunded.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        preparePaymentProviderOperationOutPort.prepareCapture(operationId, stale);

        final PaymentTransaction persisted = getPaymentInPort.getPayment(orderNumber);
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(persisted.getRefundedAmount()).isEqualByComparingTo(partial);
        assertThat(persisted.getGatewayReference()).isEqualTo(partiallyRefunded.getGatewayReference());
    }

    @Test
    void concurrentFirstCaptureMustConvergeOnOneCanonicalPaymentAndOneIntent() throws Exception {

        final String orderNumber = "B02-C-" + UUID.randomUUID().toString().substring(0, 18);
        final String operationId = "ORDER-CAPTURE:" + orderNumber;
        final BigDecimal amount = new BigDecimal("10.00");
        final CyclicBarrier start = new CyclicBarrier(2);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> {
                start.await(20, TimeUnit.SECONDS);
                return managePaymentInPort.capturePayment(orderNumber, amount, PaymentMethod.CARD);
            });
            final var second = executor.submit(() -> {
                start.await(20, TimeUnit.SECONDS);
                return managePaymentInPort.capturePayment(orderNumber, amount, PaymentMethod.CARD);
            });

            final PaymentTransaction firstResult = first.get(30, TimeUnit.SECONDS);
            final PaymentTransaction secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResult.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(secondResult.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(secondResult.getGatewayReference()).isEqualTo(firstResult.getGatewayReference());
        }

        final Integer paymentRows = jdbcTemplate.queryForObject(
                "select count(*) from test_db.PAYMENT_TRANSACTION where ORDER_NUMBER = ?",
                Integer.class,
                orderNumber);
        final Integer reconciliationRows = jdbcTemplate.queryForObject(
                "select count(*) from test_db.PAYMENT_RECONCILIATION_OPERATION where OPERATION_ID = ?",
                Integer.class,
                operationId);
        assertThat(paymentRows).isEqualTo(1);
        assertThat(reconciliationRows).isEqualTo(1);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void shouldRollbackPendingPaymentWhenReconciliationIntentPreparationFails() {

        final String orderNumber = "A1-" + UUID.randomUUID().toString().substring(0, 24);
        final String operationId = "ORDER-CAPTURE:" + orderNumber;

        doThrow(new IllegalStateException("intent write failed")).when(reconciliationOutPort)
                .start(operationId, orderNumber, PaymentProviderOperationType.CAPTURE, null);

        assertThatThrownBy(() -> managePaymentInPort.capturePayment(orderNumber, new BigDecimal("10.00"), PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("intent write failed");

        final Integer paymentRows = jdbcTemplate.queryForObject(
                "select count(*) from test_db.PAYMENT_TRANSACTION where ORDER_NUMBER = ?",
                Integer.class,
                orderNumber);
        final Integer reconciliationRows = jdbcTemplate.queryForObject(
                "select count(*) from test_db.PAYMENT_RECONCILIATION_OPERATION where OPERATION_ID = ?",
                Integer.class,
                operationId);

        assertThat(paymentRows).isZero();
        assertThat(reconciliationRows).isZero();
        verifyNoInteractions(chargePaymentOutPort);
    }
}
