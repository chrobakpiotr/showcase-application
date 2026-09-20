package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagePaymentUseCaseMutationTest {

    private static final String ORDER = "ORDER-1";
    private static final String CAPTURE_ID = "ORDER-CAPTURE:" + ORDER;
    private static final String REFUND_ID = "RETURN-1";
    private static final String GATEWAY = "gw-1";
    private static final BigDecimal AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal REFUND = new BigDecimal("20.00");
    private static final Instant CREATED = Instant.parse("2026-09-20T12:00:00Z");

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
    void shouldCompleteReconciliationForEveryTerminalCaptureState() {
        for (final PaymentStatus status : List
                .of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED, PaymentStatus.DECLINED)) {
            final PaymentTransaction existing = payment(status, CREATED);
            given(findPaymentTransactionOutPort.find(ORDER)).willReturn(existing);

            assertThat(useCase.capturePayment(ORDER, AMOUNT, PaymentMethod.CARD)).isSameAs(existing);
            verify(managePaymentReconciliationOutPort).complete(CAPTURE_ID);
            org.mockito.Mockito.reset(managePaymentReconciliationOutPort);
        }

        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
    }

    @Test
    void shouldReusePersistedPendingIdentityAndCompleteSuccessfulCapture() {
        final PaymentTransaction pending = payment(PaymentStatus.PENDING, CREATED);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        given(chargePaymentOutPort.charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD)).willReturn(GATEWAY);
        given(savePaymentTransactionOutPort.saveCaptureResult(any())).willAnswer(invocation -> invocation.getArgument(0));

        final PaymentTransaction captured = useCase.capturePayment(ORDER, AMOUNT, PaymentMethod.CARD);

        verify(savePaymentTransactionOutPort, never()).save(any());
        verify(preparePaymentProviderOperationOutPort, never()).prepareCapture(any(), any());
        verify(managePaymentReconciliationOutPort, never()).start(any(), any(), any(), any());
        verify(managePaymentReconciliationOutPort).complete(CAPTURE_ID);
        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.getGatewayReference()).isEqualTo(GATEWAY);
        assertThat(captured.getCreated()).isEqualTo(CREATED);
    }

    @Test
    void shouldRejectReuseOfCaptureIdentityWithDifferentAmountOrMethod() {
        final PaymentTransaction pending = payment(PaymentStatus.PENDING, CREATED);

        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        assertThatThrownBy(() -> useCase.capturePayment(ORDER, new BigDecimal("50.01"), PaymentMethod.CARD))
                .isInstanceOf(PaymentOperationConflictException.class);

        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        assertThatThrownBy(() -> useCase.capturePayment(ORDER, AMOUNT, PaymentMethod.PAYPAL))
                .isInstanceOf(PaymentOperationConflictException.class);

        verify(chargePaymentOutPort, never()).charge(any(), any(), any(), any());
        verify(managePaymentReconciliationOutPort, never()).start(any(), any(), any(), any());
    }

    @Test
    void shouldPersistDeclinedStateAndCompleteItsReconciliation() {
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(null);
        given(preparePaymentProviderOperationOutPort.prepareCapture(eq(CAPTURE_ID), any()))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(chargePaymentOutPort.charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD))
                .willThrow(new PaymentDeclinedException("declined"));
        given(savePaymentTransactionOutPort.saveCaptureResult(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> useCase.capturePayment(ORDER, AMOUNT, PaymentMethod.CARD))
                .isInstanceOf(PaymentDeclinedException.class);

        final ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(savePaymentTransactionOutPort).saveCaptureResult(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(captor.getValue().getCreated()).isNotNull();
        verify(managePaymentReconciliationOutPort).complete(CAPTURE_ID);
    }

    @Test
    void shouldCompleteReconciliationForAlreadyCompletedRefund() {
        final PaymentTransaction completed = payment(PaymentStatus.PARTIALLY_REFUNDED, CREATED);
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER, REFUND)).willReturn(
                new PaymentRefundClaim(PaymentRefundOutcome.COMPLETED, REFUND_ID, ORDER, REFUND, GATEWAY, completed));

        assertThat(useCase.refundPayment(ORDER, REFUND_ID, REFUND)).isSameAs(completed);

        verify(managePaymentReconciliationOutPort).complete(REFUND_ID);
        verify(managePaymentReconciliationOutPort, never()).start(any(), any(), any(), any());
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
    }

    @Test
    void shouldRunAndCompleteFullReconciliationLifecycleForReservedRefund() {
        final PaymentTransaction completed = payment(PaymentStatus.PARTIALLY_REFUNDED, CREATED);
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER, REFUND)).willReturn(
                new PaymentRefundClaim(
                        PaymentRefundOutcome.RESERVED,
                        REFUND_ID,
                        ORDER,
                        REFUND,
                        GATEWAY,
                        payment(PaymentStatus.CAPTURED, CREATED)));
        given(managePaymentRefundOutPort.complete(REFUND_ID)).willReturn(completed);

        assertThat(useCase.refundPayment(ORDER, REFUND_ID, REFUND)).isSameAs(completed);

        verify(managePaymentReconciliationOutPort).start(REFUND_ID, ORDER, PaymentProviderOperationType.REFUND, REFUND_ID);
        verify(refundPaymentOutPort).refund(ORDER, GATEWAY, REFUND_ID, REFUND);
        verify(managePaymentRefundOutPort).complete(REFUND_ID);
        verify(managePaymentReconciliationOutPort).complete(REFUND_ID);
    }

    @Test
    void shouldDoNothingProviderSideWhenNothingCanBeRefunded() {
        given(managePaymentRefundOutPort.reserve(REFUND_ID, ORDER, REFUND)).willReturn(
                new PaymentRefundClaim(PaymentRefundOutcome.NOTHING_TO_REFUND, REFUND_ID, ORDER, BigDecimal.ZERO, null, null));
        final PaymentTransaction current = payment(PaymentStatus.REFUNDED, CREATED);
        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(current);

        assertThat(useCase.refundPayment(ORDER, REFUND_ID, REFUND)).isSameAs(current);

        verify(managePaymentReconciliationOutPort, never()).start(any(), any(), any(), any());
        verify(managePaymentReconciliationOutPort, never()).complete(any());
        verify(refundPaymentOutPort, never()).refund(any(), any(), any(), any());
        verify(managePaymentRefundOutPort, never()).complete(any());
    }

    @Test
    void shouldExposeBothPendingRefundBooleanOutcomes() {
        given(managePaymentRefundOutPort.hasPending(ORDER)).willReturn(true, false);

        assertThat(useCase.hasPendingRefunds(ORDER)).isTrue();
        assertThat(useCase.hasPendingRefunds(ORDER)).isFalse();
    }

    @Test
    void shouldPreserveRequestedOrderAndInsertionOrderInBatchLookup() {
        final String other = "ORDER-2";
        final PaymentTransaction existing = payment(PaymentStatus.CAPTURED, CREATED);
        given(findPaymentTransactionOutPort.findAll(List.of(other, ORDER))).willReturn(Map.of(ORDER, existing));

        final Map<String, PaymentTransaction> result = useCase.getPayments(List.of(other, ORDER));

        assertThat(result.keySet()).containsExactly(other, ORDER);
        assertThat(result.get(other).getOrderNumber()).isEqualTo(other);
        assertThat(result.get(other).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.get(ORDER)).isSameAs(existing);
    }

    private static PaymentTransaction payment(final PaymentStatus status, final Instant created) {
        final BigDecimal refunded = switch (status) {
        case REFUNDED -> AMOUNT;
        case PARTIALLY_REFUNDED -> REFUND;
        default -> BigDecimal.ZERO;
        };
        return PaymentTransaction.builder()
                .orderNumber(ORDER)
                .amount(AMOUNT)
                .refundedAmount(refunded)
                .method(PaymentMethod.CARD)
                .status(status)
                .gatewayReference(GATEWAY)
                .created(created)
                .build();
    }
}
