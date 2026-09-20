package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayOperationLedgerCoverageTest {

    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-1";
    private static final BigDecimal AMOUNT = new BigDecimal("42.00");

    @Test
    void shouldReportZeroBeforeCommitAndOneAfterStableReplay() {

        final MockPaymentGatewayOperationLedger ledger = new MockPaymentGatewayOperationLedger();

        assertThat(ledger.providerMutationCount(OPERATION_ID)).isZero();

        final String first = ledger.replayCapture(OPERATION_ID, AMOUNT, PaymentMethod.CARD);
        final String replayed = ledger.replayCapture(OPERATION_ID, new BigDecimal("42.0"), PaymentMethod.CARD);

        assertThat(first).isEqualTo("mock-gw-" + OPERATION_ID);
        assertThat(replayed).isEqualTo(first);
        assertThat(ledger.providerMutationCount(OPERATION_ID)).isEqualTo(1);
    }

    @Test
    void shouldRejectCommittedIdentityWithDifferentAmountOrMethod() {

        final MockPaymentGatewayOperationLedger amountLedger = new MockPaymentGatewayOperationLedger();
        amountLedger.replayCapture(OPERATION_ID, AMOUNT, PaymentMethod.CARD);

        assertThatThrownBy(() -> amountLedger.replayCapture(OPERATION_ID, new BigDecimal("43.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentOperationConflictException.class);

        final MockPaymentGatewayOperationLedger methodLedger = new MockPaymentGatewayOperationLedger();
        methodLedger.replayCapture(OPERATION_ID, AMOUNT, PaymentMethod.CARD);

        assertThatThrownBy(() -> methodLedger.replayCapture(OPERATION_ID, AMOUNT, PaymentMethod.PAYPAL))
                .isInstanceOf(PaymentOperationConflictException.class);
    }
}
