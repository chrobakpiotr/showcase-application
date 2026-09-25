package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentRefundEntityRepository;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentTransactionEntityRepository;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.CompleteRefundReturnContinuationInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    private static final String ORDER_1 = "ORDER-1";
    private static final String ORDER_2 = "ORDER-2";
    private static final String MISSING = "missing";
    private static final String RETURN_FAIL = "RETURN-FAIL";
    private static final String CAPTURE_ID = "ORDER-CAPTURE:ORDER-1";
    private static final BigDecimal CAPTURE_AMOUNT = new BigDecimal("10.00");

    @Mock
    private PaymentReconciliationArbitrator arbitrator;
    @Mock
    private PaymentReconciliationEntityRepository reconciliationRepository;
    @Mock
    private PaymentTransactionEntityRepository paymentRepository;
    @Mock
    private PaymentRefundEntityRepository refundRepository;
    @Mock
    private ManageOrderInPort manageOrderInPort;
    @Mock
    private ManagePaymentInPort managePaymentInPort;
    @Mock
    private ManageRefundReturnContinuationOutPort refundReturnContinuationOutPort;
    @Mock
    private CompleteRefundReturnContinuationInPort completeRefundReturnContinuationInPort;

    @Test
    void shouldReplayCaptureAndRefundWithStableIdentityAndOwningClaim() {
        final String captureId = CAPTURE_ID;
        final String refundId = "RETURN-1";
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(captureId, refundId));
        given(arbitrator.claim(captureId)).willReturn("claim-capture");
        given(arbitrator.claim(refundId)).willReturn("claim-refund");
        given(reconciliationRepository.findById(captureId))
                .willReturn(Optional.of(operation(captureId, ORDER_1, PaymentProviderOperationType.CAPTURE, null)));
        given(paymentRepository.findById(ORDER_1)).willReturn(
                Optional.of(
                        PaymentTransactionEntity.builder()
                                .orderNumber(ORDER_1)
                                .amount(CAPTURE_AMOUNT)
                                .refundedAmount(BigDecimal.ZERO)
                                .method(PaymentMethod.CARD)
                                .status(PaymentStatus.PENDING)
                                .build()));
        given(reconciliationRepository.findById(refundId))
                .willReturn(Optional.of(operation(refundId, ORDER_2, PaymentProviderOperationType.REFUND, refundId)));
        given(refundRepository.findById(refundId)).willReturn(
                Optional.of(
                        PaymentRefundEntity.builder()
                                .refundId(refundId)
                                .orderNumber(ORDER_2)
                                .amount(new BigDecimal("4.00"))
                                .status(PaymentRefundStatus.PENDING)
                                .build()));

        scheduler().reconcileDueOperations();

        verify(managePaymentInPort).recoverCapturePaymentPendingCompletion(
                ORDER_1,
                CAPTURE_AMOUNT,
                PaymentMethod.CARD,
                new PaymentRecoveryContext(captureId, "claim-capture"));
        verify(managePaymentInPort).recoverRefundPayment(
                ORDER_2,
                refundId,
                new BigDecimal("4.00"),
                new PaymentRecoveryContext(refundId, "claim-refund"));
        verify(arbitrator).complete(captureId, "claim-capture");
        verify(arbitrator).complete(refundId, "claim-refund");
    }

    @Test
    void shouldRefundRecoveredCaptureForCancelledOrderBeforeCompletingReconciliation() {
        final String operationId = CAPTURE_ID;
        final String claimId = "claim-cancelled";
        final PaymentTransaction recovered = PaymentTransaction.builder()
                .orderNumber(ORDER_1)
                .status(PaymentStatus.CAPTURED)
                .build();
        final Order cancelled = mock(Order.class);
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(operationId));
        given(arbitrator.claim(operationId)).willReturn(claimId);
        given(reconciliationRepository.findById(operationId))
                .willReturn(Optional.of(operation(operationId, ORDER_1, PaymentProviderOperationType.CAPTURE, null)));
        given(paymentRepository.findById(ORDER_1)).willReturn(
                Optional.of(
                        PaymentTransactionEntity.builder()
                                .orderNumber(ORDER_1)
                                .amount(CAPTURE_AMOUNT)
                                .method(PaymentMethod.CARD)
                                .status(PaymentStatus.CAPTURED)
                                .build()));
        given(
                managePaymentInPort.recoverCapturePaymentPendingCompletion(
                        ORDER_1,
                        CAPTURE_AMOUNT,
                        PaymentMethod.CARD,
                        new PaymentRecoveryContext(operationId, claimId)))
                .willReturn(recovered);
        given(manageOrderInPort.findOrder(ORDER_1)).willReturn(cancelled);
        given(cancelled.getStatus()).willReturn(OrderStatus.CANCELLED);

        scheduler().reconcileDueOperations();

        final var calls = inOrder(managePaymentInPort, manageOrderInPort, arbitrator);
        calls.verify(managePaymentInPort)
                .recoverCapturePaymentPendingCompletion(
                        ORDER_1,
                        CAPTURE_AMOUNT,
                        PaymentMethod.CARD,
                        new PaymentRecoveryContext(operationId, claimId));
        calls.verify(manageOrderInPort).findOrder(ORDER_1);
        calls.verify(managePaymentInPort).refundPayment(ORDER_1);
        calls.verify(arbitrator).complete(operationId, claimId);
    }

    @Test
    void shouldKeepCaptureReconciliationPendingWhenCancelledRefundContinuationFails() {
        final String operationId = CAPTURE_ID;
        final String claimId = "claim-refund-failure";
        final PaymentTransaction recovered = PaymentTransaction.builder()
                .orderNumber(ORDER_1)
                .status(PaymentStatus.CAPTURED)
                .build();
        final Order cancelled = mock(Order.class);
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(operationId));
        given(arbitrator.claim(operationId)).willReturn(claimId);
        given(reconciliationRepository.findById(operationId))
                .willReturn(Optional.of(operation(operationId, ORDER_1, PaymentProviderOperationType.CAPTURE, null)));
        given(paymentRepository.findById(ORDER_1)).willReturn(
                Optional.of(
                        PaymentTransactionEntity.builder()
                                .orderNumber(ORDER_1)
                                .amount(CAPTURE_AMOUNT)
                                .method(PaymentMethod.CARD)
                                .status(PaymentStatus.CAPTURED)
                                .build()));
        given(
                managePaymentInPort.recoverCapturePaymentPendingCompletion(
                        ORDER_1,
                        CAPTURE_AMOUNT,
                        PaymentMethod.CARD,
                        new PaymentRecoveryContext(operationId, claimId)))
                .willReturn(recovered);
        given(manageOrderInPort.findOrder(ORDER_1)).willReturn(cancelled);
        given(cancelled.getStatus()).willReturn(OrderStatus.CANCELLED);
        org.mockito.BDDMockito.willThrow(new IllegalStateException("refund continuation unavailable"))
                .given(managePaymentInPort)
                .refundPayment(ORDER_1);

        scheduler().reconcileDueOperations();

        verify(arbitrator, never()).complete(operationId, claimId);
        verify(arbitrator).recordFailure(operationId, claimId, "refund continuation unavailable");
    }

    @Test
    void shouldRefundPartiallyRefundedRecoveredCaptureForCancelledOrder() {
        final String claimId = "claim-partially-refunded";
        final PaymentTransaction recovered = PaymentTransaction.builder()
                .orderNumber(ORDER_1)
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .build();
        final Order cancelled = mock(Order.class);

        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(CAPTURE_ID));
        given(arbitrator.claim(CAPTURE_ID)).willReturn(claimId);
        given(reconciliationRepository.findById(CAPTURE_ID))
                .willReturn(Optional.of(operation(CAPTURE_ID, ORDER_1, PaymentProviderOperationType.CAPTURE, null)));
        given(paymentRepository.findById(ORDER_1)).willReturn(
                Optional.of(
                        PaymentTransactionEntity.builder()
                                .orderNumber(ORDER_1)
                                .amount(CAPTURE_AMOUNT)
                                .method(PaymentMethod.CARD)
                                .status(PaymentStatus.CAPTURED)
                                .build()));
        given(
                managePaymentInPort.recoverCapturePaymentPendingCompletion(
                        ORDER_1,
                        CAPTURE_AMOUNT,
                        PaymentMethod.CARD,
                        new PaymentRecoveryContext(CAPTURE_ID, claimId)))
                .willReturn(recovered);
        given(manageOrderInPort.findOrder(ORDER_1)).willReturn(cancelled);
        given(cancelled.getStatus()).willReturn(OrderStatus.CANCELLED);

        scheduler().reconcileDueOperations();

        verify(managePaymentInPort).refundPayment(ORDER_1);
        verify(arbitrator).complete(CAPTURE_ID, claimId);
    }

    @Test
    void shouldSkipCancellationLookupForNonRefundableRecoveredCapture() {
        final String claimId = "claim-already-refunded";
        final PaymentTransaction recovered = PaymentTransaction.builder()
                .orderNumber(ORDER_1)
                .status(PaymentStatus.REFUNDED)
                .build();

        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(CAPTURE_ID));
        given(arbitrator.claim(CAPTURE_ID)).willReturn(claimId);
        given(reconciliationRepository.findById(CAPTURE_ID))
                .willReturn(Optional.of(operation(CAPTURE_ID, ORDER_1, PaymentProviderOperationType.CAPTURE, null)));
        given(paymentRepository.findById(ORDER_1)).willReturn(
                Optional.of(
                        PaymentTransactionEntity.builder()
                                .orderNumber(ORDER_1)
                                .amount(CAPTURE_AMOUNT)
                                .method(PaymentMethod.CARD)
                                .status(PaymentStatus.CAPTURED)
                                .build()));
        given(
                managePaymentInPort.recoverCapturePaymentPendingCompletion(
                        ORDER_1,
                        CAPTURE_AMOUNT,
                        PaymentMethod.CARD,
                        new PaymentRecoveryContext(CAPTURE_ID, claimId)))
                .willReturn(recovered);

        scheduler().reconcileDueOperations();

        verify(manageOrderInPort, never()).findOrder(ORDER_1);
        verify(managePaymentInPort, never()).refundPayment(ORDER_1);
        verify(arbitrator).complete(CAPTURE_ID, claimId);
    }

    @Test
    void shouldContinueCompletedRefundIntoLinkedReturn() {
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of());
        given(refundReturnContinuationOutPort.findRecoverableReturnNumbers(50)).willReturn(List.of("RETURN-9"));

        scheduler().reconcileDueOperations();

        verify(completeRefundReturnContinuationInPort).complete("RETURN-9");
    }

    @Test
    void shouldFenceUnownedWorkAndRecordOwnedFailure() {
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of("skip", MISSING));
        given(arbitrator.claim("skip")).willReturn(null);
        given(arbitrator.claim(MISSING)).willReturn("claim-1");
        given(reconciliationRepository.findById(MISSING)).willReturn(Optional.empty());

        scheduler().reconcileDueOperations();

        verify(arbitrator).recordFailure(MISSING, "claim-1", "Payment reconciliation operation disappeared: missing");
    }

    @Test
    void shouldContainContinuationFailureAndKeepSchedulerAlive() {
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of());
        given(refundReturnContinuationOutPort.findRecoverableReturnNumbers(50)).willReturn(List.of(RETURN_FAIL));
        org.mockito.BDDMockito.willThrow(new IllegalStateException("continuation unavailable"))
                .given(completeRefundReturnContinuationInPort)
                .complete(RETURN_FAIL);

        scheduler().reconcileDueOperations();

        verify(completeRefundReturnContinuationInPort).complete(RETURN_FAIL);
        verify(refundReturnContinuationOutPort).recordFailure(RETURN_FAIL, "continuation unavailable");
    }

    @Test
    void shouldRecordFailureWhenCapturePaymentDisappears() {
        final String operationId = "CAPTURE-MISSING-PAYMENT";
        final String claimId = "claim-capture-missing";
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(operationId));
        given(arbitrator.claim(operationId)).willReturn(claimId);
        given(reconciliationRepository.findById(operationId))
                .willReturn(Optional.of(operation(operationId, ORDER_1, PaymentProviderOperationType.CAPTURE, null)));
        given(paymentRepository.findById(ORDER_1)).willReturn(Optional.empty());

        scheduler().reconcileDueOperations();

        verify(arbitrator).recordFailure(operationId, claimId, "Payment disappeared during capture reconciliation: " + ORDER_1);
    }

    @Test
    void shouldRecordFailureWhenRefundDisappears() {
        final String operationId = "REFUND-MISSING-ROW";
        final String refundId = "RETURN-MISSING";
        final String claimId = "claim-refund-missing";
        given(arbitrator.findDueOperationIds(50)).willReturn(List.of(operationId));
        given(arbitrator.claim(operationId)).willReturn(claimId);
        given(reconciliationRepository.findById(operationId))
                .willReturn(Optional.of(operation(operationId, ORDER_2, PaymentProviderOperationType.REFUND, refundId)));
        given(refundRepository.findById(refundId)).willReturn(Optional.empty());

        scheduler().reconcileDueOperations();

        verify(arbitrator).recordFailure(operationId, claimId, "Refund disappeared during reconciliation: " + refundId);
    }

    private PaymentReconciliationScheduler scheduler() {
        return new PaymentReconciliationScheduler(
                arbitrator,
                reconciliationRepository,
                paymentRepository,
                refundRepository,
                manageOrderInPort,
                managePaymentInPort,
                refundReturnContinuationOutPort,
                completeRefundReturnContinuationInPort);
    }

    private static PaymentReconciliationEntity operation(
            final String operationId,
            final String orderNumber,
            final PaymentProviderOperationType type,
            final String refundId) {
        return PaymentReconciliationEntity.builder()
                .operationId(operationId)
                .orderNumber(orderNumber)
                .operationType(type)
                .refundId(refundId)
                .status(PaymentReconciliationStatus.PENDING)
                .attempts(1)
                .build();
    }
}
