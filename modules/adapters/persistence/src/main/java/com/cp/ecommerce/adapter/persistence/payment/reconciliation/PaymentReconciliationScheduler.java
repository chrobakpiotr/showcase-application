package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.port.incoming.CompleteRefundReturnContinuationInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.foundation.function.RuntimeFailureBoundary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Reconciles unknown outcomes by replaying the same provider idempotency identity. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "payment.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
@SuppressWarnings("PMD.CouplingBetweenObjects")
@ConditionalOnBean(ManagePaymentInPort.class)
class PaymentReconciliationScheduler {

    private static final int BATCH_SIZE = 50;

    private final PaymentReconciliationArbitrator arbitrator;
    private final PaymentReconciliationEntityRepository reconciliationRepository;
    private final PaymentTransactionEntityRepository paymentRepository;
    private final PaymentRefundEntityRepository refundRepository;
    private final ManagePaymentInPort managePaymentInPort;
    private final ManageRefundReturnContinuationOutPort refundReturnContinuationOutPort;
    private final CompleteRefundReturnContinuationInPort completeRefundReturnContinuationInPort;

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-interval-ms:5000}")
    void reconcileDueOperations() {
        arbitrator.findDueOperationIds(BATCH_SIZE).forEach(this::reconcile);
        refundReturnContinuationOutPort.findRecoverableReturnNumbers(BATCH_SIZE).forEach(this::reconcileReturnContinuation);
    }

    private void reconcileReturnContinuation(final String returnNumber) {

        RuntimeFailureBoundary.run(
                () -> completeRefundReturnContinuationInPort.complete(returnNumber),
                exception -> log.warn("Could not complete refund continuation for return {}", returnNumber, exception));
    }

    private void reconcile(final String operationId) {
        final String claimId = arbitrator.claim(operationId);
        if (claimId == null) {
            return;
        }
        RuntimeFailureBoundary.run(
                () -> reconcileClaimed(operationId, claimId),
                exception -> arbitrator.recordFailure(operationId, claimId, exception.getMessage()));
    }

    private void reconcileClaimed(final String operationId, final String claimId) {

        final PaymentReconciliationEntity operation = reconciliationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalStateException("Payment reconciliation operation disappeared: " + operationId));
        if (operation.getOperationType() == PaymentProviderOperationType.CAPTURE) {
            final PaymentTransactionEntity payment = paymentRepository.findById(operation.getOrderNumber())
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Payment disappeared during capture reconciliation: " + operation.getOrderNumber()));
            managePaymentInPort.capturePayment(operation.getOrderNumber(), payment.getAmount(), payment.getMethod());
        } else {
            final PaymentRefundEntity refund = refundRepository.findById(operation.getRefundId())
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Refund disappeared during reconciliation: " + operation.getRefundId()));
            managePaymentInPort.refundPayment(operation.getOrderNumber(), operation.getRefundId(), refund.getAmount());
        }
        arbitrator.complete(operationId, claimId);
    }

}
