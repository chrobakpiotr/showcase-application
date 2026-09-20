package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.persistence.metrics.RecoveryMetrics;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayProviderIdentityRedTest {

    private static final String ORDER = "ORDER-1";
    private static final String CAPTURE_ID = "ORDER-CAPTURE:ORDER-1";
    private static final String REFUND_ID = "RETURN-1";

    @Mock
    private ResilientExecutor resilientExecutor;
    @Mock
    private RecoveryMetrics recoveryMetrics;

    @Test
    void shouldRejectCaptureIdentityReusedWithDifferentAmount() {

        final MockPaymentGatewayAdapter gateway = new MockPaymentGatewayAdapter(resilientExecutor, recoveryMetrics);

        gateway.charge(ORDER, CAPTURE_ID, new BigDecimal("10.00"), PaymentMethod.CARD);

        assertThatThrownBy(() -> gateway.charge(ORDER, CAPTURE_ID, new BigDecimal("11.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

    @Test
    void shouldRejectCaptureIdentityReusedWithDifferentMethod() {

        final MockPaymentGatewayAdapter gateway = new MockPaymentGatewayAdapter(resilientExecutor, recoveryMetrics);

        gateway.charge(ORDER, CAPTURE_ID, new BigDecimal("10.00"), PaymentMethod.CARD);

        assertThatThrownBy(() -> gateway.charge(ORDER, CAPTURE_ID, new BigDecimal("10.00"), PaymentMethod.PAYPAL))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

    @Test
    void shouldRejectRefundIdentityReusedWithDifferentAmount() {

        final MockPaymentGatewayAdapter gateway = new MockPaymentGatewayAdapter(resilientExecutor, recoveryMetrics);

        gateway.refund(ORDER, "gw-1", REFUND_ID, new BigDecimal("3.00"));

        assertThatThrownBy(() -> gateway.refund(ORDER, "gw-1", REFUND_ID, new BigDecimal("4.00")))
                .isInstanceOf(PaymentOperationConflictException.class);
    }
}
