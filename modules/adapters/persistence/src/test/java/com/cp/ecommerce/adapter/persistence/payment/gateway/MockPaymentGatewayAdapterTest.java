package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link MockPaymentGatewayAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayAdapterTest {

    private static final String ORDER_NUMBER = "ORDER-1001";

    private static final String ORDER_AMOUNT = "59.98";

    private static final String CAPTURE_OPERATION_ID = "ORDER-CAPTURE:" + ORDER_NUMBER;

    private static final BigDecimal DECLINE_ABOVE_AMOUNT = new BigDecimal("10000.00");

    @Mock
    private transient ResilientExecutor resilientExecutor;

    @InjectMocks
    private transient MockPaymentGatewayAdapter mockPaymentGatewayAdapter;

    @Test
    void shouldChargeAndReturnGatewayReferenceWhenAmountBelowDeclineThreshold() throws Exception {

        runResilientCallableEagerly();

        final String result = mockPaymentGatewayAdapter
                .charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, new BigDecimal(ORDER_AMOUNT), PaymentMethod.CARD);

        assertThat(result).isEqualTo("mock-gw-" + CAPTURE_OPERATION_ID);
    }

    @Test
    void shouldReturnSameGatewayReferenceForRepeatedCaptureOperation() throws Exception {

        runResilientCallableEagerly();

        final String first = mockPaymentGatewayAdapter
                .charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, new BigDecimal(ORDER_AMOUNT), PaymentMethod.CARD);
        final String second = mockPaymentGatewayAdapter
                .charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, new BigDecimal(ORDER_AMOUNT), PaymentMethod.CARD);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldDeclineChargeAboveThresholdWithoutInvokingResilientExecutor() throws Exception {

        final BigDecimal amountAboveThreshold = DECLINE_ABOVE_AMOUNT.add(BigDecimal.ONE);

        assertThatThrownBy(
                () -> mockPaymentGatewayAdapter
                        .charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, amountAboveThreshold, PaymentMethod.CARD))
                .isInstanceOf(PaymentDeclinedException.class);
        verify(resilientExecutor, never()).callResilient(anyString(), any());
    }

    @Test
    void shouldWrapUnexpectedResilientExecutorFailureAsTechnicalProblem() throws Exception {

        doThrow(new RuntimeException("gateway timeout")).when(resilientExecutor).callResilient(anyString(), any());

        assertThatThrownBy(
                () -> mockPaymentGatewayAdapter
                        .charge(ORDER_NUMBER, CAPTURE_OPERATION_ID, new BigDecimal(ORDER_AMOUNT), PaymentMethod.CARD))
                .isInstanceOf(TechnicalProblemException.class);
    }

    @Test
    void shouldRefund() {

        runResilientRunnableEagerly();

        mockPaymentGatewayAdapter.refund(ORDER_NUMBER, "mock-gw-1234", "RETURN-1", new BigDecimal("10.00"));

        verify(resilientExecutor).runResilient(anyString(), any());
    }

    @Test
    void shouldWrapUnexpectedRefundFailureAsTechnicalProblem() {

        doThrow(new RuntimeException("gateway timeout")).when(resilientExecutor).runResilient(anyString(), any());

        assertThatThrownBy(
                () -> mockPaymentGatewayAdapter.refund(ORDER_NUMBER, "mock-gw-1234", "RETURN-1", new BigDecimal("10.00")))
                .isInstanceOf(TechnicalProblemException.class);
    }

    private void runResilientCallableEagerly() throws Exception {

        doAnswer(invocation -> {
            final Callable<Object> action = invocation.getArgument(1);
            return action.call();
        }).when(resilientExecutor).callResilient(anyString(), any());
    }

    private void runResilientRunnableEagerly() {

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

}
