package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Creates the pending provider operation and its durable reconciliation intent in one short database transaction.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class PreparePaymentProviderOperationAdapter implements PreparePaymentProviderOperationOutPort {

    private static final String ORDER_CAPTURE_PREFIX = "ORDER-CAPTURE:";

    private final SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    private final ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;

    private final ManagePaymentRefundOutPort managePaymentRefundOutPort;

    @Override
    @Transactional
    public PaymentTransaction prepareCapture(final String operationId, final PaymentTransaction pendingPayment) {

        return prepareCapture(operationId, pendingPayment, null);
    }

    @Override
    @Transactional
    public PaymentTransaction prepareCapture(
            final String operationId,
            final PaymentTransaction pendingPayment,
            final PaymentRecoveryContext recoveryContext) {

        validateCaptureOperationId(operationId, pendingPayment.getOrderNumber());
        validateOptionalRecoveryContext(recoveryContext, operationId);
        final PaymentTransaction canonical = savePaymentTransactionOutPort.prepareCapture(pendingPayment);
        requireAutomaticCapturePermission(
                startReconciliation(
                        operationId,
                        canonical.getOrderNumber(),
                        PaymentProviderOperationType.CAPTURE,
                        null,
                        recoveryContext),
                operationId,
                canonical);
        return canonical;
    }

    @Override
    @Transactional
    public PaymentRefundClaim prepareRefund(final String refundId, final String orderNumber, final BigDecimal amount) {

        return prepareRefund(refundId, orderNumber, amount, null);
    }

    @Override
    @Transactional
    public PaymentRefundClaim prepareRefund(
            final String refundId,
            final String orderNumber,
            final BigDecimal amount,
            final PaymentRecoveryContext recoveryContext) {

        validateOptionalRecoveryContext(recoveryContext, refundId);
        final PaymentRefundClaim claim = amount == null
                ? managePaymentRefundOutPort.reserveRemaining(refundId, orderNumber)
                : managePaymentRefundOutPort.reserve(refundId, orderNumber, amount);
        if (claim.outcome() == PaymentRefundOutcome.RESERVED || claim.outcome() == PaymentRefundOutcome.RETRY) {
            requireAutomaticProviderPermission(
                    startReconciliation(refundId, orderNumber, PaymentProviderOperationType.REFUND, refundId, recoveryContext),
                    refundId);
        }
        return claim;
    }

    private PaymentReconciliationStartOutcome startReconciliation(
            final String operationId,
            final String orderNumber,
            final PaymentProviderOperationType type,
            final String refundId,
            final PaymentRecoveryContext recoveryContext) {

        return recoveryContext == null
                ? managePaymentReconciliationOutPort.start(operationId, orderNumber, type, refundId)
                : managePaymentReconciliationOutPort.startOwned(operationId, orderNumber, type, refundId, recoveryContext);
    }

    private static void requireAutomaticCapturePermission(
            final PaymentReconciliationStartOutcome outcome,
            final String operationId,
            final PaymentTransaction canonical) {

        if (outcome == PaymentReconciliationStartOutcome.READY || outcome == PaymentReconciliationStartOutcome.CURRENT_OWNER) {
            return;
        }
        if (outcome == PaymentReconciliationStartOutcome.COMPLETED && isTerminalCapture(canonical)) {
            return;
        }
        throw new PaymentOperationConflictException(
                "Payment provider operation " + operationId + " is not eligible for automatic replay: " + outcome);
    }

    private static boolean isTerminalCapture(final PaymentTransaction payment) {

        return payment.getStatus() == com.cp.ecommerce.domain.payment.PaymentStatus.CAPTURED
                || payment.getStatus() == com.cp.ecommerce.domain.payment.PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == com.cp.ecommerce.domain.payment.PaymentStatus.REFUNDED
                || payment.getStatus() == com.cp.ecommerce.domain.payment.PaymentStatus.DECLINED;
    }

    private static void requireAutomaticProviderPermission(
            final PaymentReconciliationStartOutcome outcome,
            final String operationId) {

        if (outcome != PaymentReconciliationStartOutcome.READY && outcome != PaymentReconciliationStartOutcome.CURRENT_OWNER) {
            throw new PaymentOperationConflictException(
                    "Payment provider operation " + operationId + " is not eligible for automatic replay: " + outcome);
        }
    }

    private static void validateCaptureOperationId(final String operationId, final String orderNumber) {

        if (!Objects.equals(operationId, ORDER_CAPTURE_PREFIX + orderNumber)) {

            throw new PaymentOperationConflictException("Capture operation identity does not match order " + orderNumber);
        }
    }

    private static void validateOptionalRecoveryContext(
            final PaymentRecoveryContext recoveryContext,
            final String operationId) {

        if (recoveryContext != null && !Objects.equals(recoveryContext.operationId(), operationId)) {
            throw new PaymentOperationConflictException(
                    "Recovery operation identity " + recoveryContext.operationId() + " does not match provider operation "
                            + operationId);
        }
    }
}
