package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentRefundOutcome;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagePaymentUseCaseTest {

    private static final String ORDER_NUMBER = "ORDER-1001";
    private static final String CAPTURE_OPERATION_ID = "ORDER-CAPTURE:" + ORDER_NUMBER;
    private static final String GATEWAY_REFERENCE = "mock-gw-1";
    private static final String REFUND_ID = "RETURN-1";
    private static final BigDecimal AMOUNT = new BigDecimal("59.98");
    private static final BigDecimal PARTIAL = new BigDecimal("29.99");

    @Mock
    private transient FindPaymentTransactionOutPort findPaymentTransactionOutPort;

    @Mock
    private transient SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    @Mock
    private transient ChargePaymentOutPort chargePaymentOutPort;

    @Mock
    private transient RefundPaymentOutPort refundPaymentOutPort;

    @Mock
    private transient ManagePaymentRefundOutPort managePaymentRefundOutPort;

    @InjectMocks
    private transient ManagePaymentUseCase managePaymentUseCase;

    @Test
    void shouldReturnPendingPlaceholderWhenNoTransactionRecordedYet() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);

        final PaymentTransaction result = managePaymentUseCase.getPayment(ORDER_NUMBER);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getRefundedAmount()).isZero();
        assertThat(result.getRemainingRefundableAmount()).isZero();
    }

    @Test
    void shouldReturnPersistedTransactionWhenPresent() {

        final PaymentTransaction existing = captured();
        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(existing);

        assertThat(managePaymentUseCase.getPayment(ORDER_NUMBER)).isSameAs(existing);
    }

    @Test
    void shouldCaptureNewPayment() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);
        given(chargePaymentOutPort.charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, AMOUNT, PaymentMethod.CARD))
                .willReturn(GATEWAY_REFERENCE);
        given(savePaymentTransactionOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final PaymentTransaction result = managePaymentUseCase.capturePayment(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(result.getRefundedAmount()).isZero();
    }

    @Test
    void shouldNotChargeCapturedPaymentAgain() {

        assertCaptureIsNoOp(captured());
    }

    @Test
    void shouldNotChargePartiallyRefundedPaymentAgain() {

        assertCaptureIsNoOp(
                PaymentTransaction.builder()
                        .orderNumber(ORDER_NUMBER)
                        .amount(AMOUNT)
                        .refundedAmount(PARTIAL)
                        .method(PaymentMethod.CARD)
                        .status(PaymentStatus.PARTIALLY_REFUNDED)
                        .gatewayReference(GATEWAY_REFERENCE)
                        .build());
    }

    @Test
    void shouldNotChargeRefundedPaymentAgain() {

        assertCaptureIsNoOp(
                PaymentTransaction.builder()
                        .orderNumber(ORDER_NUMBER)
                        .amount(AMOUNT)
                        .refundedAmount(AMOUNT)
                        .method(PaymentMethod.CARD)
                        .status(PaymentStatus.REFUNDED)
                        .gatewayReference(GATEWAY_REFERENCE)
                        .build());
    }

    @Test
    void shouldRecordDecline() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);
        given(chargePaymentOutPort.charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, AMOUNT, PaymentMethod.CARD))
                .willThrow(new PaymentDeclinedException("declined"));
        given(savePaymentTransactionOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> managePaymentUseCase.capturePayment(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD))
                .isInstanceOf(PaymentDeclinedException.class);
        verify(savePaymentTransactionOutPort).save(any());
    }

    @Test
    void shouldRefundSpecificAmountAndCompleteClaim() {

        final PaymentTransaction completed = partiallyRefunded();
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .willReturn(claim(PaymentRefundOutcome.RESERVED, PARTIAL, captured()));
        given(managePaymentRefundOutPort.complete(REFUND_ID)).willReturn(completed);

        final PaymentTransaction result = managePaymentUseCase.refundPayment(ORDER_NUMBER, REFUND_ID, PARTIAL);

        assertThat(result).isSameAs(completed);
        verify(refundPaymentOutPort).refund(ORDER_NUMBER, GATEWAY_REFERENCE, REFUND_ID, PARTIAL);
    }

    @Test
    void shouldRetryPendingRefundWithSameProviderIdentity() {

        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .willReturn(claim(PaymentRefundOutcome.RETRY, PARTIAL, captured()));
        given(managePaymentRefundOutPort.complete(REFUND_ID)).willReturn(partiallyRefunded());

        managePaymentUseCase.refundPayment(ORDER_NUMBER, REFUND_ID, PARTIAL);

        verify(refundPaymentOutPort).refund(ORDER_NUMBER, GATEWAY_REFERENCE, REFUND_ID, PARTIAL);
    }

    @Test
    void shouldNotCallGatewayForCompletedRefund() {

        final PaymentTransaction completed = partiallyRefunded();
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER_NUMBER, PARTIAL))
                .willReturn(claim(PaymentRefundOutcome.COMPLETED, PARTIAL, completed));

        assertThat(managePaymentUseCase.refundPayment(ORDER_NUMBER, REFUND_ID, PARTIAL)).isSameAs(completed);
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
    }

    @Test
    void shouldRefundRemainingAmountForWholeOrderRefund() {

        final String fullRefundId = "ORDER-REFUND:" + ORDER_NUMBER;
        given(managePaymentRefundOutPort.reserveRemaining(fullRefundId, ORDER_NUMBER)).willReturn(
                new PaymentRefundClaim(
                        PaymentRefundOutcome.RESERVED,
                        fullRefundId,
                        ORDER_NUMBER,
                        PARTIAL,
                        GATEWAY_REFERENCE,
                        partiallyRefunded()));
        given(managePaymentRefundOutPort.complete(fullRefundId)).willReturn(
                PaymentTransaction.builder()
                        .orderNumber(ORDER_NUMBER)
                        .amount(AMOUNT)
                        .refundedAmount(AMOUNT)
                        .method(PaymentMethod.CARD)
                        .status(PaymentStatus.REFUNDED)
                        .gatewayReference(GATEWAY_REFERENCE)
                        .build());

        final PaymentTransaction result = managePaymentUseCase.refundPayment(ORDER_NUMBER);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(refundPaymentOutPort).refund(ORDER_NUMBER, GATEWAY_REFERENCE, fullRefundId, PARTIAL);
    }

    @Test
    void shouldPreserveWholeOrderNoOpWhenNothingCanBeRefunded() {

        final String fullRefundId = "ORDER-REFUND:" + ORDER_NUMBER;
        given(managePaymentRefundOutPort.reserveRemaining(fullRefundId, ORDER_NUMBER)).willReturn(
                new PaymentRefundClaim(
                        PaymentRefundOutcome.NOTHING_TO_REFUND,
                        fullRefundId,
                        ORDER_NUMBER,
                        BigDecimal.ZERO,
                        null,
                        null));
        final PaymentTransaction pending = PaymentTransaction.builder().orderNumber(ORDER_NUMBER).build();
        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(pending);

        assertThat(managePaymentUseCase.refundPayment(ORDER_NUMBER)).isSameAs(pending);
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
    }

    private void assertCaptureIsNoOp(final PaymentTransaction existing) {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(existing);

        assertThat(managePaymentUseCase.capturePayment(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD)).isSameAs(existing);
        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
    }

    private static PaymentTransaction captured() {

        return PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(AMOUNT)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference(GATEWAY_REFERENCE)
                .build();
    }

    private static PaymentTransaction partiallyRefunded() {

        return PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(AMOUNT)
                .refundedAmount(PARTIAL)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .gatewayReference(GATEWAY_REFERENCE)
                .build();
    }

    private static PaymentRefundClaim claim(
            final PaymentRefundOutcome outcome,
            final BigDecimal amount,
            final PaymentTransaction payment) {

        return new PaymentRefundClaim(outcome, REFUND_ID, ORDER_NUMBER, amount, GATEWAY_REFERENCE, payment);
    }
}
