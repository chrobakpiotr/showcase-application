package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Creates the pending capture row and its reconciliation intent in one short database transaction.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class PreparePaymentProviderOperationAdapter implements PreparePaymentProviderOperationOutPort {

    private final SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    private final ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;

    private final ManagePaymentRefundOutPort managePaymentRefundOutPort;

    @Override
    @Transactional
    public PaymentTransaction prepareCapture(final String operationId, final PaymentTransaction pendingPayment) {

        final PaymentTransaction persisted = savePaymentTransactionOutPort.save(pendingPayment);
        managePaymentReconciliationOutPort
                .start(operationId, persisted.getOrderNumber(), PaymentProviderOperationType.CAPTURE, null);
        return persisted;
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
