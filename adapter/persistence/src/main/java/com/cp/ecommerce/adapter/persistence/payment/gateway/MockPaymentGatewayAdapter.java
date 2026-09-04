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
 * Mock/simulated payment gateway adapter (see ADR 0030) - this showcase has no real payment provider integration, so this
 * stands in for one while still exercising the same resilience conventions ({@link ResilientExecutor}) used by every other
 * outbound adapter (e.g. {@code SendEmailAdapter}).
 *
 * <p>
 * The decline rule is a deterministic amount threshold rather than random/simulated flakiness, so that tests relying on it stay
 * reproducible: any charge above {@link #declineAboveAmount} is declined, exactly like a real gateway would reject an
 * implausibly large charge. This is a genuine business decline (see {@link PaymentDeclinedException}), so it is thrown
 * immediately, outside of {@link ResilientExecutor}'s retry - retrying a deterministic decline could never succeed.
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
    public void refund(final String orderNumber, final String gatewayReference) {

        try {

            resilientExecutor.runResilient(
                    REFUND_RESILIENCE_INSTANCE_NAME,
                    () -> log.info("Mock payment gateway refunded {} for order: {}", gatewayReference, orderNumber));
        } catch (final RuntimeException exception) {

            throw new TechnicalProblemException("Could not refund payment for order: " + orderNumber, exception);
        }
    }

}
