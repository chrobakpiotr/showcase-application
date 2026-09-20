package com.cp.ecommerce.adapter.persistence.payment;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
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

    @Override
    @Transactional
    public PaymentTransaction prepareCapture(final String operationId, final PaymentTransaction pendingPayment) {

        final PaymentTransaction persisted = savePaymentTransactionOutPort.save(pendingPayment);
        managePaymentReconciliationOutPort
                .start(operationId, persisted.getOrderNumber(), PaymentProviderOperationType.CAPTURE, null);
        return persisted;
    }
}
