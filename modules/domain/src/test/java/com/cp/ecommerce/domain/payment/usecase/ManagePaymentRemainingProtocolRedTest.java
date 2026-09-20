package com.cp.ecommerce.domain.payment.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentRefundClaim;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.FindPaymentTransactionOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentRefundOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.PreparePaymentProviderOperationOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.SavePaymentTransactionOutPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagePaymentRemainingProtocolRedTest {

    private static final String ORDER = "ORDER-A1-REMAINING";
    private static final String CAPTURE_ID = "ORDER-CAPTURE:" + ORDER;
    private static final BigDecimal AMOUNT = new BigDecimal("42.00");

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
    void shouldRepairHistoricalPendingCaptureIntentBeforeProviderIo() {

        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(ORDER)
                .amount(AMOUNT)
                .refundedAmount(BigDecimal.ZERO)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .created(Instant.parse("2026-09-20T12:00:00Z"))
                .build();

        given(findPaymentTransactionOutPort.find(ORDER)).willReturn(pending);
        given(preparePaymentProviderOperationOutPort.prepareCapture(CAPTURE_ID, pending)).willReturn(pending);
        given(chargePaymentOutPort.charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD))
                .willThrow(new IllegalStateException("stop after durable prepare"));

        assertThatThrownBy(() -> useCase.capturePayment(ORDER, AMOUNT, PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stop after durable prepare");

        verify(preparePaymentProviderOperationOutPort).prepareCapture(CAPTURE_ID, pending);
        verify(chargePaymentOutPort).charge(ORDER, CAPTURE_ID, AMOUNT, PaymentMethod.CARD);
    }

    @Test
    void shouldExposeAtomicRefundPrepareOnProviderOperationBoundary() {

        assertThat(
                Arrays.stream(PreparePaymentProviderOperationOutPort.class.getMethods())
                        .filter(method -> method.getName().equals("prepareRefund")))
                .as("refund reservation + reconciliation intent must be prepared atomically before provider I/O")
                .anySatisfy(method -> {
                    assertThat(method.getReturnType()).isEqualTo(PaymentRefundClaim.class);
                    assertThat(method.getParameterTypes()).contains(String.class, BigDecimal.class);
                });
    }
}
