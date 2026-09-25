package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagePaymentRecoveryContextTest {

    private static final String ORDER = "ORDER-S22";
    private static final String CAPTURE_ID = "ORDER-CAPTURE:" + ORDER;
    private static final String REFUND_ID = "RETURN-S22";
    private static final String ORDER_REFUND_ID = "ORDER-REFUND:" + ORDER;
    private static final String CLAIM = "claim-s22";
    private static final String GATEWAY = "gateway-s22";
    private static final BigDecimal AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal REFUND = new BigDecimal("4.00");
    private static final Instant CREATED = Instant.parse("2026-09-23T07:00:00Z");

    @Mock
    private FindPaymentTransactionOutPort findPaymentTransactionOutPort;
    @Mock
    private SavePaymentTransactionOutPort savePaymentTransactionOutPort;
    @Mock
    private ChargePaymentOutPort chargePaymentOutPort;
    @Mock
    private RefundPaymentOutPort refundPaymentOutPort;
    @Mock
    private ManagePaymentRefundOutPort managePaymentRefundOutPort;
    @Mock
    private ManagePaymentReconciliationOutPort managePaymentReconciliationOutPort;
    @Mock
    private PreparePaymentProviderOperationOutPort preparePaymentProviderOperationOutPort;

    private ManagePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManagePaymentUseCase(
                findPaymentTransactionOutPort,
                savePaymentTransactionOutPort,
                chargePaymentOutPort,
                refundPaymentOutPort,
                managePaymentRefundOutPort,
                managePaymentReconciliationOutPort,
                preparePaymentProviderOperationOutPort);
    }

    @Test
    void shouldCarryCaptureOwnerThroughPrepareAndCompletion() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction pending = payment(PaymentStatus.PENDING, BigDecimal.ZERO);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        given(preparePaymentProviderOperationOutPort.prepareCapture(CAPTURE_ID, pending, context)).willReturn(pending);
        given(chargePaymentOutPort.charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD)).willReturn(GATEWAY);
        given(savePaymentTransactionOutPort.saveCaptureResult(any())).willAnswer(invocation -> invocation.getArgument(0));

        final PaymentTransaction result = useCase.recoverCapturePayment(ORDER, AMOUNT, PaymentMethod.CARD, context);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(preparePaymentProviderOperationOutPort).prepareCapture(CAPTURE_ID, pending, context);
        verify(managePaymentReconciliationOutPort).completeOwned(context);
        verify(managePaymentReconciliationOutPort, never()).complete(CAPTURE_ID);
    }

    @Test
    void shouldDeferCaptureCompletionForDurableContinuation() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction pending = payment(PaymentStatus.PENDING, BigDecimal.ZERO);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        given(preparePaymentProviderOperationOutPort.prepareCapture(CAPTURE_ID, pending, context)).willReturn(pending);
        given(chargePaymentOutPort.charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD)).willReturn(GATEWAY);
        given(savePaymentTransactionOutPort.saveCaptureResult(any())).willAnswer(inv -> inv.getArgument(0));

        final PaymentTransaction result = useCase
                .recoverCapturePaymentPendingCompletion(ORDER, AMOUNT, PaymentMethod.CARD, context);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(preparePaymentProviderOperationOutPort).prepareCapture(CAPTURE_ID, pending, context);
        verify(managePaymentReconciliationOutPort, never()).completeOwned(context);
        verify(managePaymentReconciliationOutPort, never()).complete(CAPTURE_ID);
    }

    @Test
    void shouldValidateRecoveryOwnerBeforeDeferredTerminalCaptureContinuation() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction captured = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(captured);
        given(preparePaymentProviderOperationOutPort.prepareCapture(CAPTURE_ID, captured, context)).willReturn(captured);

        assertThat(useCase.recoverCapturePaymentPendingCompletion(ORDER, AMOUNT, PaymentMethod.CARD, context))
                .isSameAs(captured);

        verify(preparePaymentProviderOperationOutPort).prepareCapture(CAPTURE_ID, captured, context);
        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
        verify(managePaymentReconciliationOutPort, never()).completeOwned(context);
    }

    @Test
    void shouldRejectDeferredTerminalReplayWhenOwnerPrepareChangesFingerprint() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction captured = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        final PaymentTransaction conflicting = PaymentTransaction.builder()
                .orderNumber(ORDER)
                .amount(AMOUNT.add(BigDecimal.ONE))
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference(GATEWAY)
                .created(CREATED)
                .build();
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(captured);
        given(preparePaymentProviderOperationOutPort.prepareCapture(CAPTURE_ID, captured, context)).willReturn(conflicting);

        assertThatThrownBy(() -> useCase.recoverCapturePaymentPendingCompletion(ORDER, AMOUNT, PaymentMethod.CARD, context))
                .isInstanceOf(PaymentOperationConflictException.class);

        verify(managePaymentReconciliationOutPort, never()).completeOwned(context);
        verify(managePaymentReconciliationOutPort, never()).complete(CAPTURE_ID);
        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
    }

    @Test
    void shouldReturnDeferredDeclineWithoutClosingReconciliation() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction pending = payment(PaymentStatus.PENDING, BigDecimal.ZERO);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        given(preparePaymentProviderOperationOutPort.prepareCapture(CAPTURE_ID, pending, context)).willReturn(pending);
        given(chargePaymentOutPort.charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD))
                .willThrow(new PaymentDeclinedException("declined"));
        given(savePaymentTransactionOutPort.saveCaptureResult(any())).willAnswer(invocation -> invocation.getArgument(0));

        final PaymentTransaction result = useCase
                .recoverCapturePaymentPendingCompletion(ORDER, AMOUNT, PaymentMethod.CARD, context);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        verify(managePaymentReconciliationOutPort, never()).completeOwned(context);
        verify(managePaymentReconciliationOutPort, never()).complete(CAPTURE_ID);
    }

    @Test
    void shouldRejectMismatchedRecoveryIdentityBeforeDeferredCaptureContinuation() {
        final PaymentRecoveryContext wrong = new PaymentRecoveryContext("OTHER", CLAIM);

        assertThatThrownBy(() -> useCase.recoverCapturePaymentPendingCompletion(ORDER, AMOUNT, PaymentMethod.CARD, wrong))
                .isInstanceOf(PaymentOperationConflictException.class);

        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
        verify(managePaymentReconciliationOutPort, never()).completeOwned(any());
        verify(managePaymentReconciliationOutPort, never()).complete(any());
    }

    @Test
    void shouldPrepareCancelledCaptureRefundUnderCaptureOwnerBeforeProviderIo() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction captured = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        final PaymentTransaction refunded = payment(PaymentStatus.REFUNDED, AMOUNT);
        given(preparePaymentProviderOperationOutPort.prepareRefundAfterCaptureRecovery(ORDER_REFUND_ID, ORDER, context))
                .willReturn(
                        new PaymentRefundClaim(
                                PaymentRefundOutcome.RESERVED,
                                ORDER_REFUND_ID,
                                ORDER,
                                AMOUNT,
                                GATEWAY,
                                captured));
        given(managePaymentRefundOutPort.complete(ORDER_REFUND_ID)).willReturn(refunded);

        assertThat(useCase.refundPaymentAfterCaptureRecovery(ORDER, context)).isSameAs(refunded);

        verify(preparePaymentProviderOperationOutPort).prepareRefundAfterCaptureRecovery(ORDER_REFUND_ID, ORDER, context);
        verify(refundPaymentOutPort).refund(ORDER, GATEWAY, ORDER_REFUND_ID, AMOUNT);
        verify(managePaymentReconciliationOutPort).complete(ORDER_REFUND_ID);
        verify(managePaymentReconciliationOutPort, never()).completeOwned(context);
    }

    @Test
    void shouldRejectMismatchedCaptureRecoveryIdentityBeforePreparingRefund() {
        final PaymentRecoveryContext wrong = new PaymentRecoveryContext("OTHER", CLAIM);

        assertThatThrownBy(() -> useCase.refundPaymentAfterCaptureRecovery(ORDER, wrong))
                .isInstanceOf(PaymentOperationConflictException.class);

        verify(preparePaymentProviderOperationOutPort, never()).prepareRefundAfterCaptureRecovery(any(), any(), any());
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
    }

    @Test
    void shouldCarryRefundOwnerThroughPrepareAndCompletion() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(REFUND_ID, CLAIM);
        final PaymentTransaction captured = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        final PaymentTransaction completed = payment(PaymentStatus.PARTIALLY_REFUNDED, REFUND);
        given(preparePaymentProviderOperationOutPort.prepareRefund(REFUND_ID, ORDER, REFUND, context))
                .willReturn(new PaymentRefundClaim(PaymentRefundOutcome.RETRY, REFUND_ID, ORDER, REFUND, GATEWAY, captured));
        given(managePaymentRefundOutPort.complete(REFUND_ID)).willReturn(completed);

        assertThat(useCase.recoverRefundPayment(ORDER, REFUND_ID, REFUND, context)).isSameAs(completed);

        verify(refundPaymentOutPort).refund(ORDER, GATEWAY, REFUND_ID, REFUND);
        verify(managePaymentReconciliationOutPort).completeOwned(context);
        verify(managePaymentReconciliationOutPort, never()).complete(REFUND_ID);
    }

    @Test
    void shouldCompleteTerminalCaptureWithOwnerWithoutProviderIo() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(CAPTURE_ID, CLAIM);
        final PaymentTransaction captured = payment(PaymentStatus.CAPTURED, BigDecimal.ZERO);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(captured);

        assertThat(useCase.recoverCapturePayment(ORDER, AMOUNT, PaymentMethod.CARD, context)).isSameAs(captured);

        verify(managePaymentReconciliationOutPort).completeOwned(context);
        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
    }

    @Test
    void shouldCompleteAlreadyFinishedRefundWithOwnerWithoutProviderIo() {
        final PaymentRecoveryContext context = new PaymentRecoveryContext(REFUND_ID, CLAIM);
        final PaymentTransaction completed = payment(PaymentStatus.PARTIALLY_REFUNDED, REFUND);
        given(preparePaymentProviderOperationOutPort.prepareRefund(REFUND_ID, ORDER, REFUND, context)).willReturn(
                new PaymentRefundClaim(PaymentRefundOutcome.COMPLETED, REFUND_ID, ORDER, REFUND, GATEWAY, completed));

        assertThat(useCase.recoverRefundPayment(ORDER, REFUND_ID, REFUND, context)).isSameAs(completed);

        verify(managePaymentReconciliationOutPort).completeOwned(context);
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
    }

    @Test
    void shouldRejectMismatchedRecoveryIdentity() {
        final PaymentRecoveryContext wrong = new PaymentRecoveryContext("OTHER", CLAIM);

        assertThatThrownBy(() -> useCase.recoverCapturePayment(ORDER, AMOUNT, PaymentMethod.CARD, wrong))
                .isInstanceOf(PaymentOperationConflictException.class);
        assertThatThrownBy(() -> useCase.recoverRefundPayment(ORDER, REFUND_ID, REFUND, wrong))
                .isInstanceOf(PaymentOperationConflictException.class);

        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
    }

    @Test
    void shouldRejectNullRecoveryContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> useCase.recoverCapturePayment(ORDER, AMOUNT, PaymentMethod.CARD, null));
        assertThatNullPointerException().isThrownBy(() -> useCase.recoverRefundPayment(ORDER, REFUND_ID, REFUND, null));
    }

    private static PaymentTransaction payment(final PaymentStatus status, final BigDecimal refundedAmount) {
        return PaymentTransaction.builder()
                .orderNumber(ORDER)
                .amount(AMOUNT)
                .refundedAmount(refundedAmount)
                .method(PaymentMethod.CARD)
                .status(status)
                .gatewayReference(GATEWAY)
                .created(CREATED)
                .build();
    }
}
