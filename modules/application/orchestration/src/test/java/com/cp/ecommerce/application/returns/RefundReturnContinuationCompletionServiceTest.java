package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.payment.RefundReturnContinuationIntent;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundReturnContinuationCompletionServiceTest {

    private static final String RETURN_NUMBER = "RET-1";
    private static final String ORDER_NUMBER = "ORDER-1";
    private static final BigDecimal AMOUNT = new BigDecimal("12.00");

    @Mock
    private GetReturnInPort getReturnInPort;
    @Mock
    private ManagePaymentInPort managePaymentInPort;
    @Mock
    private ManageRefundReturnContinuationOutPort continuationOutPort;
    @Mock
    private ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Test
    void shouldResumeMissingRefundFromSelfContainedContinuationThenFinalize() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER)).willReturn(intent(RETURN_NUMBER, ORDER_NUMBER, AMOUNT));
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(request(RETURN_NUMBER, ORDER_NUMBER, AMOUNT));

        service().complete(RETURN_NUMBER);

        verify(managePaymentInPort).refundPayment(ORDER_NUMBER, RETURN_NUMBER, AMOUNT);
        verify(returnStateNotificationTransaction).markRefundedAndNotify(RETURN_NUMBER);
    }

    @Test
    void shouldCompleteZeroValueContinuationWithoutProviderIo() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER))
                .willReturn(intent(RETURN_NUMBER, ORDER_NUMBER, BigDecimal.ZERO));
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(request(RETURN_NUMBER, ORDER_NUMBER, BigDecimal.ZERO));

        service().complete(RETURN_NUMBER);

        verify(managePaymentInPort, never()).refundPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(returnStateNotificationTransaction).markRefundedAndNotify(RETURN_NUMBER);
    }

    @Test
    void shouldRejectMissingContinuationIntent() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service().complete(RETURN_NUMBER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RETURN_NUMBER);
    }

    @Test
    void shouldRejectMissingReturnRequest() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER)).willReturn(intent(RETURN_NUMBER, ORDER_NUMBER, AMOUNT));
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service().complete(RETURN_NUMBER)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldRejectIntentWhoseReturnNumberNoLongerMatchesRma() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER)).willReturn(intent("OTHER-RETURN", ORDER_NUMBER, AMOUNT));
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(request(RETURN_NUMBER, ORDER_NUMBER, AMOUNT));

        assertConflict();
    }

    @Test
    void shouldRejectIntentWhoseOrderNoLongerMatchesRma() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER)).willReturn(intent(RETURN_NUMBER, "OTHER-ORDER", AMOUNT));
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(request(RETURN_NUMBER, ORDER_NUMBER, AMOUNT));

        assertConflict();
    }

    @Test
    void shouldRejectIntentWhoseAmountNoLongerMatchesRma() {
        given(continuationOutPort.findByReturnNumber(RETURN_NUMBER))
                .willReturn(intent(RETURN_NUMBER, ORDER_NUMBER, new BigDecimal("11.00")));
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(request(RETURN_NUMBER, ORDER_NUMBER, AMOUNT));

        assertConflict();
    }

    private void assertConflict() {
        assertThatThrownBy(() -> service().complete(RETURN_NUMBER)).isInstanceOf(PaymentRefundConflictException.class);
        verify(managePaymentInPort, never()).refundPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(returnStateNotificationTransaction, never()).markRefundedAndNotify(RETURN_NUMBER);
    }

    private RefundReturnContinuationCompletionService service() {
        return new RefundReturnContinuationCompletionService(
                getReturnInPort,
                managePaymentInPort,
                continuationOutPort,
                returnStateNotificationTransaction);
    }

    private static RefundReturnContinuationIntent intent(
            final String returnNumber,
            final String orderNumber,
            final BigDecimal amount) {
        return new RefundReturnContinuationIntent(RETURN_NUMBER, returnNumber, orderNumber, amount);
    }

    private static ReturnRequest request(final String returnNumber, final String orderNumber, final BigDecimal amount) {
        return ReturnRequest.builder()
                .returnNumber(returnNumber)
                .orderNumber(orderNumber)
                .sku("SKU-1")
                .quantity(1)
                .reason("test")
                .status(ReturnStatus.APPROVED)
                .refundAmount(amount)
                .build();
    }
}
