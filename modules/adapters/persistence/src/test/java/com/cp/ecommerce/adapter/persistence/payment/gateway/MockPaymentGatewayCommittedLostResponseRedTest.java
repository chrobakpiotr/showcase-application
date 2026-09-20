package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayCommittedLostResponseRedTest {

    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-1";
    private static final BigDecimal AMOUNT = new BigDecimal("42.00");

    @Test
    void shouldReplayCommittedCaptureAfterLostResponseWithoutSecondProviderMutation() throws Exception {

        final Class<?> ledgerType = Class
                .forName("com.cp.ecommerce.adapter.persistence.payment.gateway.MockPaymentGatewayOperationLedger");
        final Constructor<?> constructor = ledgerType.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object ledger = constructor.newInstance();

        final Method commitThenLoseResponse = ledgerType
                .getDeclaredMethod("commitCaptureThenLoseResponse", String.class, BigDecimal.class, PaymentMethod.class);
        commitThenLoseResponse.setAccessible(true);

        final Method replayCapture = ledgerType
                .getDeclaredMethod("replayCapture", String.class, BigDecimal.class, PaymentMethod.class);
        replayCapture.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                commitThenLoseResponse.invoke(ledger, OPERATION_ID, AMOUNT, PaymentMethod.CARD);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }).isInstanceOf(RuntimeException.class).hasMessageContaining("lost");

        final String replayedGatewayReference = (String) replayCapture.invoke(ledger, OPERATION_ID, AMOUNT, PaymentMethod.CARD);

        assertThat(replayedGatewayReference).isEqualTo("mock-gw-" + OPERATION_ID);

        final Method providerMutationCount = ledgerType.getDeclaredMethod("providerMutationCount", String.class);
        providerMutationCount.setAccessible(true);

        assertThat(providerMutationCount.invoke(ledger, OPERATION_ID)).isEqualTo(1);
    }

    @Test
    void shouldRejectSameCommittedIdentityWithDifferentCaptureParameters() throws Exception {

        final Class<?> ledgerType = Class
                .forName("com.cp.ecommerce.adapter.persistence.payment.gateway.MockPaymentGatewayOperationLedger");
        final Constructor<?> constructor = ledgerType.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object ledger = constructor.newInstance();

        final Method commitThenLoseResponse = ledgerType
                .getDeclaredMethod("commitCaptureThenLoseResponse", String.class, BigDecimal.class, PaymentMethod.class);
        commitThenLoseResponse.setAccessible(true);

        try {
            commitThenLoseResponse.invoke(ledger, OPERATION_ID, AMOUNT, PaymentMethod.CARD);
        } catch (java.lang.reflect.InvocationTargetException expected) {
            // First response is deliberately lost after provider-side commit.
        }

        final Method replayCapture = ledgerType
                .getDeclaredMethod("replayCapture", String.class, BigDecimal.class, PaymentMethod.class);
        replayCapture.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                replayCapture.invoke(ledger, OPERATION_ID, new BigDecimal("43.00"), PaymentMethod.CARD);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }).isInstanceOf(RuntimeException.class);
    }
}
