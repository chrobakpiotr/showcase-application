package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PreparePaymentProviderOperationAdapterTest {

    private static final String ORDER_NUMBER = "ORDER-1001";
    private static final String OPERATION_ID = "ORDER-CAPTURE:" + ORDER_NUMBER;

    @Mock
    private SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    @Mock
    private ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;

    @Mock
    private ManagePaymentRefundOutPort managePaymentRefundOutPort;

    @Test
    void shouldPersistPendingPaymentBeforePreparingReconciliationIntent() {

        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(new BigDecimal("10.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .build();
        given(savePaymentTransactionOutPort.prepareCapture(pending)).willReturn(pending);

        final PaymentTransaction result = new PreparePaymentProviderOperationAdapter(
                savePaymentTransactionOutPort,
                managePaymentReconciliationOutPort,
                managePaymentRefundOutPort).prepareCapture(OPERATION_ID, pending);

        assertThat(result).isSameAs(pending);
        verify(managePaymentReconciliationOutPort)
                .start(OPERATION_ID, ORDER_NUMBER, PaymentProviderOperationType.CAPTURE, null);
    }

    @Test
    void shouldRejectMismatchedCaptureOperationIdentity() {

        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(new BigDecimal("10.00"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .build();
        final PreparePaymentProviderOperationAdapter adapter = new PreparePaymentProviderOperationAdapter(
                savePaymentTransactionOutPort,
                managePaymentReconciliationOutPort,
                managePaymentRefundOutPort);

        assertThatThrownBy(() -> adapter.prepareCapture("ORDER-CAPTURE:OTHER", pending))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

}
