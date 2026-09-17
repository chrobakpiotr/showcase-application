package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;

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

/**
 * Tests for {@link ManagePaymentUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManagePaymentUseCaseTest {

    private static final String ORDER_NUMBER = "ORDER-1001";

    private static final BigDecimal AMOUNT = new BigDecimal("59.98");

    @Mock
    private transient FindPaymentTransactionOutPort findPaymentTransactionOutPort;

    @Mock
    private transient SavePaymentTransactionOutPort savePaymentTransactionOutPort;

    @Mock
    private transient ChargePaymentOutPort chargePaymentOutPort;

    @Mock
    private transient RefundPaymentOutPort refundPaymentOutPort;

    @InjectMocks
    private transient ManagePaymentUseCase managePaymentUseCase;

    @Test
    void shouldReturnPendingPlaceholderWhenNoTransactionRecordedYet() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);

        final PaymentTransaction result = managePaymentUseCase.getPayment(ORDER_NUMBER);

        assertThat(result.getOrderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldReturnPersistedTransactionWhenPresent() {

        final PaymentTransaction existing = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(AMOUNT)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference("mock-gw-1")
                .build();
        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(existing);

        final PaymentTransaction result = managePaymentUseCase.getPayment(ORDER_NUMBER);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void shouldCaptureNewPaymentAndPersistItAsCaptured() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);
        given(chargePaymentOutPort.charge(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD)).willReturn("mock-gw-1");
        given(savePaymentTransactionOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final PaymentTransaction result = managePaymentUseCase.capturePayment(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(result.getGatewayReference()).isEqualTo("mock-gw-1");
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    void shouldNotChargeTwiceWhenAlreadyCaptured() {

        final PaymentTransaction existing = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(AMOUNT)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference("mock-gw-1")
                .build();
        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(existing);

        final PaymentTransaction result = managePaymentUseCase.capturePayment(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD);

        assertThat(result).isSameAs(existing);
        verify(chargePaymentOutPort, never()).charge(any(), any(), any());
        verify(savePaymentTransactionOutPort, never()).save(any());
    }

    @Test
    void shouldRecordDeclinedTransactionAndPropagateExceptionOnDecline() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);
        given(chargePaymentOutPort.charge(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD))
                .willThrow(new PaymentDeclinedException("declined"));
        given(savePaymentTransactionOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> managePaymentUseCase.capturePayment(ORDER_NUMBER, AMOUNT, PaymentMethod.CARD))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(savePaymentTransactionOutPort).save(any());
    }

    @Test
    void shouldRefundCapturedPayment() {

        final PaymentTransaction existing = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(AMOUNT)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference("mock-gw-1")
                .build();
        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(existing);
        given(savePaymentTransactionOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final PaymentTransaction result = managePaymentUseCase.refundPayment(ORDER_NUMBER);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(refundPaymentOutPort).refund(ORDER_NUMBER, "mock-gw-1");
    }

    @Test
    void shouldNoOpRefundWhenNeverCaptured() {

        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(null);

        final PaymentTransaction result = managePaymentUseCase.refundPayment(ORDER_NUMBER);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(refundPaymentOutPort, never()).refund(any(), any());
        verify(savePaymentTransactionOutPort, never()).save(any());
    }

    @Test
    void shouldNoOpRefundWhenAlreadyRefunded() {

        final PaymentTransaction existing = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .amount(AMOUNT)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.REFUNDED)
                .gatewayReference("mock-gw-1")
                .build();
        given(findPaymentTransactionOutPort.find(ORDER_NUMBER)).willReturn(existing);

        final PaymentTransaction result = managePaymentUseCase.refundPayment(ORDER_NUMBER);

        assertThat(result).isSameAs(existing);
        verify(refundPaymentOutPort, never()).refund(any(), any());
        verify(savePaymentTransactionOutPort, never()).save(any());
    }

}
