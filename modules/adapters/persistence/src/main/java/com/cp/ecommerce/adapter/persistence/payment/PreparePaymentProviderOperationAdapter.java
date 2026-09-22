package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
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
 * Creates the pending capture row and its reconciliation intent in one short database transaction.
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

        validateCaptureOperationId(operationId, pendingPayment.getOrderNumber());
        final PaymentTransaction canonical = savePaymentTransactionOutPort.prepareCapture(pendingPayment);
        requireAutomaticCapturePermission(
                managePaymentReconciliationOutPort
                        .start(operationId, canonical.getOrderNumber(), PaymentProviderOperationType.CAPTURE, null),
                operationId,
                canonical);
        return canonical;
    }

    private static void requireAutomaticCapturePermission(
            final PaymentReconciliationStartOutcome outcome,
            final String operationId,
            final PaymentTransaction canonical) {

        if (outcome == PaymentReconciliationStartOutcome.READY) {
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

        if (outcome != PaymentReconciliationStartOutcome.READY) {
            throw new PaymentOperationConflictException(
                    "Payment provider operation " + operationId + " is not eligible for automatic replay: " + outcome);
        }
    }

    private static void validateCaptureOperationId(final String operationId, final String orderNumber) {

        if (!Objects.equals(operationId, ORDER_CAPTURE_PREFIX + orderNumber)) {

            throw new PaymentOperationConflictException("Capture operation identity does not match order " + orderNumber);
        }
    }

    @Override
    @Transactional
    public PaymentRefundClaim prepareRefund(final String refundId, final String orderNumber, final BigDecimal amount) {

        final PaymentRefundClaim claim = amount == null
                ? managePaymentRefundOutPort.reserveRemaining(refundId, orderNumber)
                : managePaymentRefundOutPort.reserve(refundId, orderNumber, amount);
        if (claim.outcome() == PaymentRefundOutcome.RESERVED || claim.outcome() == PaymentRefundOutcome.RETRY) {
            requireAutomaticProviderPermission(
                    managePaymentReconciliationOutPort
                            .start(refundId, orderNumber, PaymentProviderOperationType.REFUND, refundId),
                    refundId);
        }
        return claim;
    }

}
