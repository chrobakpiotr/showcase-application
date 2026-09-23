package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReturnApprovalRefundPreparationTransactionTest {

    private static final String RETURN_NUMBER = "RET-1";
    private static final String ORDER_NUMBER = "ORDER-1";
    private static final BigDecimal POSITIVE_AMOUNT = new BigDecimal("10.00");

    @Mock
    private ReturnModerationInPort returnModerationInPort;
    @Mock
    private ManageRefundReturnContinuationOutPort continuationOutPort;
    @Mock
    private ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Test
    void shouldPersistPositiveRefundIntentBeforeProviderIo() {
        final ReturnRequest approved = request(ReturnStatus.APPROVED, POSITIVE_AMOUNT);
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(approved);

        assertThat(service().approveAndPrepare(RETURN_NUMBER)).isSameAs(approved);

        verify(continuationOutPort).start(RETURN_NUMBER, RETURN_NUMBER, ORDER_NUMBER, POSITIVE_AMOUNT);
        verify(returnStateNotificationTransaction, never()).markRefundedAndNotify(RETURN_NUMBER);
    }

    @Test
    void shouldFinishZeroValueRmaLocally() {
        final ReturnRequest approved = request(ReturnStatus.APPROVED, BigDecimal.ZERO);
        final ReturnRequest refunded = request(ReturnStatus.REFUNDED, BigDecimal.ZERO);
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(approved);
        given(returnStateNotificationTransaction.markRefundedAndNotify(RETURN_NUMBER)).willReturn(refunded);

        assertThat(service().approveAndPrepare(RETURN_NUMBER)).isSameAs(refunded);

        verify(continuationOutPort, never()).start(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnNullWithoutCreatingContinuationWhenReturnIsMissing() {
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(null);

        assertThat(service().approveAndPrepare(RETURN_NUMBER)).isNull();

        verifyNoInteractions(continuationOutPort, returnStateNotificationTransaction);
    }

    @Test
    void shouldReplayAlreadyRefundedReturnWithoutCreatingContinuation() {
        final ReturnRequest refunded = request(ReturnStatus.REFUNDED, POSITIVE_AMOUNT);
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(refunded);

        assertThat(service().approveAndPrepare(RETURN_NUMBER)).isSameAs(refunded);

        verifyNoInteractions(continuationOutPort, returnStateNotificationTransaction);
    }

    private ReturnApprovalRefundPreparationTransaction service() {
        return new ReturnApprovalRefundPreparationTransaction(
                returnModerationInPort,
                continuationOutPort,
                returnStateNotificationTransaction);
    }

    private static ReturnRequest request(final ReturnStatus status, final BigDecimal amount) {
        return ReturnRequest.builder()
                .returnNumber(RETURN_NUMBER)
                .orderNumber(ORDER_NUMBER)
                .sku("SKU-1")
                .quantity(1)
                .reason("test")
                .status(status)
                .refundAmount(amount)
                .build();
    }
}
