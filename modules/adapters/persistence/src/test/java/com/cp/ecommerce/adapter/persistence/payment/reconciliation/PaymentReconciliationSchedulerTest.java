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
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    private static final String ORDER_1 = "ORDER-1";
    private static final String ORDER_2 = "ORDER-2";
    private static final String MISSING = "missing";

    @Mock
    private PaymentReconciliationArbitrator arbitrator;
    @Mock
    private PaymentReconciliationEntityRepository reconciliationRepository;
    @Mock
    private PaymentTransactionEntityRepository paymentRepository;
    @Mock
    private PaymentRefundEntityRepository refundRepository;
    @Mock
    private ManagePaymentInPort managePaymentInPort;

    @Test
    void shouldReplayCaptureAndRefundWithStableIdentity() {
        final String captureId = "ORDER-CAPTURE:ORDER-1";
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
                                .amount(new BigDecimal("10.00"))
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

        verify(managePaymentInPort).capturePayment(ORDER_1, new BigDecimal("10.00"), PaymentMethod.CARD);
        verify(managePaymentInPort).refundPayment(ORDER_2, refundId, new BigDecimal("4.00"));
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
                managePaymentInPort);
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
