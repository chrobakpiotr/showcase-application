package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
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
class PreparePaymentProviderOperationOwnerTest {

    private static final String ORDER = "ORDER-S22";
    private static final String CAPTURE_ID = "ORDER-CAPTURE:" + ORDER;
    private static final String REFUND_ID = "RETURN-S22";
    private static final String CLAIM = "claim-s22";
    private static final BigDecimal AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal REFUND = new BigDecimal("4.00");

    @Mock
    private SavePaymentTransactionOutPort savePaymentTransactionOutPort;
    @Mock
    private ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;
    @Mock
    private ManagePaymentRefundOutPort managePaymentRefundOutPort;

    @Test
    void shouldAuthorizeCurrentOwnerCapture() {
        final PaymentTransaction pending = pending();
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        given(savePaymentTransactionOutPort.prepareCapture(pending)).willReturn(pending);
        given(
                managePaymentReconciliationOutPort
                        .startOwned(CAPTURE_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context))
                .willReturn(PaymentReconciliationStartOutcome.CURRENT_OWNER);

        assertThat(adapter().prepareCapture(CAPTURE_ID, pending, context)).isSameAs(pending);

        verify(managePaymentReconciliationOutPort)
                .startOwned(CAPTURE_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context);
    }

    @Test
    void shouldRejectLostOwnerCaptureAndMismatchedRecoveryIdentity() {
        final PaymentTransaction pending = pending();
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        given(savePaymentTransactionOutPort.prepareCapture(pending)).willReturn(pending);
        given(
                managePaymentReconciliationOutPort
                        .startOwned(CAPTURE_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context))
                .willReturn(PaymentReconciliationStartOutcome.LOST_CLAIM);

        assertThatThrownBy(() -> adapter().prepareCapture(CAPTURE_ID, pending, context))
                .isInstanceOf(PaymentOperationConflictException.class)
                .hasMessageContaining("LOST_CLAIM");

        assertThatThrownBy(() -> adapter().prepareCapture(CAPTURE_ID, pending, new PaymentRecoveryContext("OTHER", CLAIM)))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

    @Test
    void shouldAuthorizeCurrentOwnerRefund() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(REFUND_ID, CLAIM);
        final PaymentRefundClaim claim = new PaymentRefundClaim(
                PaymentRefundOutcome.RETRY,
                REFUND_ID,
                ORDER,
                REFUND,
                "gateway",
                captured());
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER, REFUND)).willReturn(claim);
        given(
                managePaymentReconciliationOutPort
                        .startOwned(REFUND_ID, ORDER, PaymentProviderOperationType.REFUND, REFUND_ID, context))
                .willReturn(PaymentReconciliationStartOutcome.CURRENT_OWNER);

        assertThat(adapter().prepareRefund(REFUND_ID, ORDER, REFUND, context)).isSameAs(claim);
    }

    @Test
    void shouldRejectLostOwnerRefundAndMismatchedRecoveryIdentity() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(REFUND_ID, CLAIM);
        final PaymentRefundClaim claim = new PaymentRefundClaim(
                PaymentRefundOutcome.RESERVED,
                REFUND_ID,
                ORDER,
                REFUND,
                "gateway",
                captured());
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER, REFUND)).willReturn(claim);
        given(
                managePaymentReconciliationOutPort
                        .startOwned(REFUND_ID, ORDER, PaymentProviderOperationType.REFUND, REFUND_ID, context))
                .willReturn(PaymentReconciliationStartOutcome.LOST_CLAIM);

        assertThatThrownBy(() -> adapter().prepareRefund(REFUND_ID, ORDER, REFUND, context))
                .isInstanceOf(PaymentOperationConflictException.class)
                .hasMessageContaining("LOST_CLAIM");

        assertThatThrownBy(() -> adapter().prepareRefund(REFUND_ID, ORDER, REFUND, new PaymentRecoveryContext("OTHER", CLAIM)))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

    private PreparePaymentProviderOperationAdapter adapter() {
        return new PreparePaymentProviderOperationAdapter(
                savePaymentTransactionOutPort,
                managePaymentReconciliationOutPort,
                managePaymentRefundOutPort);
    }

    private static PaymentTransaction pending() {
        return PaymentTransaction.builder()
                .orderNumber(ORDER)
                .amount(AMOUNT)
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .build();
    }

    private static PaymentTransaction captured() {
        return PaymentTransaction.builder()
                .orderNumber(ORDER)
                .amount(AMOUNT)
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference("gateway")
                .build();
    }
}
