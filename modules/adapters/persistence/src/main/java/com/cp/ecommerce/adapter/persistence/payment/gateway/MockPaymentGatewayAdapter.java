package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.exception.PaymentDeclinedException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;

import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock payment gateway. Refund requests carry a provider idempotency key.
 */
@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
class MockPaymentGatewayAdapter implements ChargePaymentOutPort, RefundPaymentOutPort {

    private static final String CHARGE_RESILIENCE_INSTANCE_NAME = "chargePayment";

    private static final String REFUND_RESILIENCE_INSTANCE_NAME = "refundPayment";

    private final ResilientExecutor resilientExecutor;

    @Value("${payment.gateway.mock.decline-above:10000.00}")
    private BigDecimal declineAboveAmount = new BigDecimal("10000.00");

    @Override
    public String charge(final String orderNumber, final BigDecimal amount, final PaymentMethod method) {

        if (amount.compareTo(declineAboveAmount) > 0) {

            throw new PaymentDeclinedException(
                    "Payment gateway declined charge of " + amount + " for order: " + orderNumber + " via " + method);
        }
        try {

            final String gatewayReference = resilientExecutor
                    .callResilient(CHARGE_RESILIENCE_INSTANCE_NAME, () -> "mock-gw-" + UUID.randomUUID());
            log.info(
                    "Mock payment gateway captured {} for order: {} via {} ({})",
                    amount,
                    orderNumber,
                    method,
                    gatewayReference);
            return gatewayReference;
        } catch (final Exception exception) {

            throw new TechnicalProblemException("Could not charge payment for order: " + orderNumber, exception);
        }
    }

    @Override
    public void refund(
            final String orderNumber,
            final String gatewayReference,
            final String refundId,
            final BigDecimal amount) {

        try {

            resilientExecutor.runResilient(
                    REFUND_RESILIENCE_INSTANCE_NAME,
                    () -> log.info(
                            "Mock payment gateway refunded {} for order: {} ({}, idempotencyKey={})",
                            amount,
                            orderNumber,
                            gatewayReference,
                            refundId));
        } catch (final RuntimeException exception) {

            throw new TechnicalProblemException("Could not refund payment for order: " + orderNumber, exception);
        }
    }
}
