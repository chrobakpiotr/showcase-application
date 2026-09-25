package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.application.EcommerceApplication;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.CompleteRefundReturnContinuationInPort;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(classes = EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "payment.reconciliation.enabled=false",
                "payment.reconciliation.retry-backoff-ms=5000",
                "payment.reconciliation.lease-ms=30000",
                "payment.reconciliation.max-attempts=10" })
class PaymentReconciliationOwnerRecoveryPostgresIntegrationTest {

    private static final BigDecimal CAPTURE_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal REFUND_AMOUNT = new BigDecimal("4.00");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private ChargePaymentOutPort chargePaymentOutPort;

    @Autowired
    private RefundPaymentOutPort refundPaymentOutPort;

    @Autowired
    private PaymentReconciliationArbitrator arbitrator;

    @Autowired
    private PaymentReconciliationEntityRepository reconciliationRepository;

    @Autowired
    private PaymentTransactionEntityRepository paymentRepository;

    @Autowired
    private PaymentRefundEntityRepository refundRepository;

    @Autowired
    private ManageOrderInPort manageOrderInPort;
    @Autowired
    private ManagePaymentInPort managePaymentInPort;

    @Autowired
    private ManageRefundReturnContinuationOutPort refundReturnContinuationOutPort;

    @Autowired
    private CompleteRefundReturnContinuationInPort completeRefundReturnContinuationInPort;

    @Autowired
    private PreparePaymentProviderOperationOutPort preparePaymentProviderOperationOutPort;

    @Autowired
    private GetPaymentInPort getPaymentInPort;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void currentOwnerRecoversPendingCapture() {
        final String orderNumber = unique("CAPTURE");
        final String operationId = captureId(orderNumber);
        preparePendingCapture(orderNumber);
        given(chargePaymentOutPort.charge(orderNumber, operationId, CAPTURE_AMOUNT, PaymentMethod.CARD))
                .willReturn("gateway-capture");

        scheduler().reconcileDueOperations();

        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(reconciliation(operationId).getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
        verify(chargePaymentOutPort).charge(orderNumber, operationId, CAPTURE_AMOUNT, PaymentMethod.CARD);
    }

    @Test
    void currentOwnerRecoversPendingRefundExactlyOnce() {
        final String orderNumber = unique("REFUND");
        final String refundId = "RETURN-" + UUID.randomUUID().toString().substring(0, 18);
        capture(orderNumber, "gateway-refund");
        preparePaymentProviderOperationOutPort.prepareRefund(refundId, orderNumber, REFUND_AMOUNT);

        scheduler().reconcileDueOperations();

        final PaymentTransaction payment = getPaymentInPort.getPayment(orderNumber);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo(REFUND_AMOUNT);
        assertThat(refundRepository.findById(refundId).orElseThrow().getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(reconciliation(refundId).getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
        verify(refundPaymentOutPort).refund(orderNumber, "gateway-refund", refundId, REFUND_AMOUNT);
    }

    @Test
    void ordinaryClientCannotUseAnotherWorkersClaim() {
        final String orderNumber = unique("CLIENT");
        final String operationId = captureId(orderNumber);
        preparePendingCapture(orderNumber);
        final String claimId = arbitrator.claim(operationId);
        assertThat(claimId).isNotBlank();

        assertThatThrownBy(() -> managePaymentInPort.capturePayment(orderNumber, CAPTURE_AMOUNT, PaymentMethod.CARD))
                .isInstanceOf(PaymentOperationConflictException.class)
                .hasMessageContaining("BUSY");

        final PaymentReconciliationEntity persisted = reconciliation(operationId);
        assertThat(persisted.getStatus()).isEqualTo(PaymentReconciliationStatus.PENDING);
        assertThat(persisted.getClaimId()).isEqualTo(claimId);
        verifyNoInteractions(chargePaymentOutPort);
    }

    @Test
    void staleOwnerCannotCrossTakeoverBoundary() {
        final String orderNumber = unique("TAKEOVER");
        final String operationId = captureId(orderNumber);
        preparePendingCapture(orderNumber);
        final String claimA = arbitrator.claim(operationId);
        assertThat(claimA).isNotBlank();

        final PaymentReconciliationEntity expired = reconciliation(operationId);
        expired.setClaimUntil(Instant.EPOCH);
        reconciliationRepository.saveAndFlush(expired);

        final String claimB = arbitrator.claim(operationId);
        assertThat(claimB).isNotBlank().isNotEqualTo(claimA);

        assertThatThrownBy(
                () -> managePaymentInPort.recoverCapturePayment(
                        orderNumber,
                        CAPTURE_AMOUNT,
                        PaymentMethod.CARD,
                        new PaymentRecoveryContext(operationId, claimA)))
                .isInstanceOf(PaymentOperationConflictException.class)
                .hasMessageContaining("LOST_CLAIM");

        assertThat(reconciliation(operationId).getClaimId()).isEqualTo(claimB);
        verifyNoInteractions(chargePaymentOutPort);
    }

    @Test
    void manualReviewIsNotAutomaticallyReplayedOrClosed() {
        final String orderNumber = unique("MANUAL");
        final String operationId = captureId(orderNumber);
        preparePendingCapture(orderNumber);

        final PaymentReconciliationEntity operation = reconciliation(operationId);
        operation.setStatus(PaymentReconciliationStatus.MANUAL_REVIEW);
        operation.setLastError("operator decision required");
        reconciliationRepository.saveAndFlush(operation);

        scheduler().reconcileDueOperations();

        final PaymentReconciliationEntity persisted = reconciliation(operationId);
        assertThat(persisted.getStatus()).isEqualTo(PaymentReconciliationStatus.MANUAL_REVIEW);
        assertThat(persisted.getLastError()).isEqualTo("operator decision required");
        verifyNoInteractions(chargePaymentOutPort);
    }

    @Test
    void lostProviderResponseReplaysSameKeyWithOneEconomicEffect() {
        final String orderNumber = unique("LOST");
        final String operationId = captureId(orderNumber);
        final AtomicInteger providerCalls = new AtomicInteger();
        final AtomicInteger economicEffects = new AtomicInteger();
        final ConcurrentHashMap<String, String> accepted = new ConcurrentHashMap<>();

        given(chargePaymentOutPort.charge(orderNumber, operationId, CAPTURE_AMOUNT, PaymentMethod.CARD))
                .willAnswer(invocation -> {
                    providerCalls.incrementAndGet();
                    final String key = invocation.getArgument(1);
                    final String previous = accepted.putIfAbsent(key, "gateway-lost");
                    if (previous == null) {
                        economicEffects.incrementAndGet();
                        throw new TechnicalProblemException("provider accepted but response was lost");
                    }
                    return previous;
                });

        assertThatThrownBy(() -> managePaymentInPort.capturePayment(orderNumber, CAPTURE_AMOUNT, PaymentMethod.CARD))
                .isInstanceOf(TechnicalProblemException.class);

        scheduler().reconcileDueOperations();

        assertThat(providerCalls).hasValue(2);
        assertThat(economicEffects).hasValue(1);
        assertThat(accepted).containsOnlyKeys(operationId);
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(reconciliation(operationId).getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
    }

    @Test
    void terminalReplayDoesNotCreateAnotherProviderEffect() {
        final String orderNumber = unique("TERMINAL");
        final String operationId = captureId(orderNumber);
        capture(orderNumber, "gateway-terminal");

        org.mockito.Mockito.reset(chargePaymentOutPort);

        final PaymentTransaction replay = managePaymentInPort.recoverCapturePayment(
                orderNumber,
                CAPTURE_AMOUNT,
                PaymentMethod.CARD,
                new PaymentRecoveryContext(operationId, "historical-replay"));

        assertThat(replay.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(replay.getGatewayReference()).isEqualTo("gateway-terminal");
        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
        assertThat(reconciliation(operationId).getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
    }

    private void preparePendingCapture(final String orderNumber) {
        preparePaymentProviderOperationOutPort.prepareCapture(
                captureId(orderNumber),
                PaymentTransaction.builder()
                        .orderNumber(orderNumber)
                        .amount(CAPTURE_AMOUNT)
                        .refundedAmount(BigDecimal.ZERO)
                        .method(PaymentMethod.CARD)
                        .status(PaymentStatus.PENDING)
                        .created(Instant.parse("2026-09-23T07:00:00Z"))
                        .build());
    }

    private void capture(final String orderNumber, final String gatewayReference) {
        given(chargePaymentOutPort.charge(orderNumber, captureId(orderNumber), CAPTURE_AMOUNT, PaymentMethod.CARD))
                .willReturn(gatewayReference);
        assertThat(managePaymentInPort.capturePayment(orderNumber, CAPTURE_AMOUNT, PaymentMethod.CARD).getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
    }

    private PaymentReconciliationEntity reconciliation(final String operationId) {
        return reconciliationRepository.findById(operationId).orElseThrow();
    }

    private PaymentReconciliationScheduler scheduler() {
        return new PaymentReconciliationScheduler(
                arbitrator,
                reconciliationRepository,
                paymentRepository,
                refundRepository,
                manageOrderInPort,
                managePaymentInPort,
                refundReturnContinuationOutPort,
                completeRefundReturnContinuationInPort);
    }

    private static String captureId(final String orderNumber) {
        return "ORDER-CAPTURE:" + orderNumber;
    }

    private static String unique(final String prefix) {
        return "S22-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 16);
    }
}
