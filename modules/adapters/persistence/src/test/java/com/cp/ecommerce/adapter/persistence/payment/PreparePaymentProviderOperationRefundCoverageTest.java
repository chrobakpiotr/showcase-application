package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PreparePaymentProviderOperationRefundCoverageTest {

    private static final String REFUND_ID = "REFUND-1";
    private static final String ORDER_NUMBER = "ORDER-1";
    private static final BigDecimal AMOUNT = new BigDecimal("12.34");

    @Mock
    private SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    @Mock
    private ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;

    @Mock
    private ManagePaymentRefundOutPort managePaymentRefundOutPort;

    private PreparePaymentProviderOperationAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new PreparePaymentProviderOperationAdapter(
                savePaymentTransactionOutPort,
                managePaymentReconciliationOutPort,
                managePaymentRefundOutPort);
    }

    @Test
    void shouldReserveSpecificRefundAndStartReconciliationForReservedClaim() {

        final PaymentRefundClaim claim = claim(PaymentRefundOutcome.RESERVED);
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER_NUMBER, AMOUNT)).willReturn(claim);
        given(managePaymentReconciliationOutPort.start(REFUND_ID, ORDER_NUMBER, PaymentProviderOperationType.REFUND, REFUND_ID))
                .willReturn(PaymentReconciliationStartOutcome.READY);

        assertThat(adapter.prepareRefund(REFUND_ID, ORDER_NUMBER, AMOUNT)).isSameAs(claim);

        verify(managePaymentRefundOutPort).reserve(REFUND_ID, ORDER_NUMBER, AMOUNT);
        verify(managePaymentReconciliationOutPort)
                .start(REFUND_ID, ORDER_NUMBER, PaymentProviderOperationType.REFUND, REFUND_ID);
    }

    @Test
    void shouldReserveRemainingRefundAndStartReconciliationForRetryClaim() {

        final PaymentRefundClaim claim = claim(PaymentRefundOutcome.RETRY);
        given(managePaymentRefundOutPort.reserveRemaining(REFUND_ID, ORDER_NUMBER)).willReturn(claim);
        given(managePaymentReconciliationOutPort.start(REFUND_ID, ORDER_NUMBER, PaymentProviderOperationType.REFUND, REFUND_ID))
                .willReturn(PaymentReconciliationStartOutcome.READY);

        assertThat(adapter.prepareRefund(REFUND_ID, ORDER_NUMBER, null)).isSameAs(claim);

        verify(managePaymentRefundOutPort).reserveRemaining(REFUND_ID, ORDER_NUMBER);
        verify(managePaymentReconciliationOutPort)
                .start(REFUND_ID, ORDER_NUMBER, PaymentProviderOperationType.REFUND, REFUND_ID);
    }

    @Test
    void shouldBlockAutomaticRefundReplayWhenOperationRequiresManualReview() {

        final PaymentRefundClaim claim = claim(PaymentRefundOutcome.RETRY);
        given(managePaymentRefundOutPort.reserveRemaining(REFUND_ID, ORDER_NUMBER)).willReturn(claim);
        given(managePaymentReconciliationOutPort.start(REFUND_ID, ORDER_NUMBER, PaymentProviderOperationType.REFUND, REFUND_ID))
                .willReturn(PaymentReconciliationStartOutcome.MANUAL_REVIEW);

        assertThatThrownBy(() -> adapter.prepareRefund(REFUND_ID, ORDER_NUMBER, null))
                .isInstanceOf(PaymentOperationConflictException.class)
                .hasMessageContaining("MANUAL_REVIEW");
    }

    @Test
    void shouldNotStartReconciliationForTerminalRefundClaim() {

        final PaymentRefundClaim claim = claim(PaymentRefundOutcome.COMPLETED);
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER_NUMBER, AMOUNT)).willReturn(claim);

        assertThat(adapter.prepareRefund(REFUND_ID, ORDER_NUMBER, AMOUNT)).isSameAs(claim);

        verify(managePaymentReconciliationOutPort, never())
                .start(REFUND_ID, ORDER_NUMBER, PaymentProviderOperationType.REFUND, REFUND_ID);
    }

    private static PaymentRefundClaim claim(final PaymentRefundOutcome outcome) {

        return new PaymentRefundClaim(outcome, REFUND_ID, ORDER_NUMBER, AMOUNT, "gateway-1", null);
    }
}
