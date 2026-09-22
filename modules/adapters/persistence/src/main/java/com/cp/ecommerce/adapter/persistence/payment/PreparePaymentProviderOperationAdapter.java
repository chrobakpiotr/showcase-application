package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
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
        managePaymentReconciliationOutPort
                .start(operationId, canonical.getOrderNumber(), PaymentProviderOperationType.CAPTURE, null);
        return canonical;
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
            managePaymentReconciliationOutPort.start(refundId, orderNumber, PaymentProviderOperationType.REFUND, refundId);
        }
        return claim;
    }

}
