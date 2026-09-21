package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.persistence.metrics.RecoveryMetrics;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.port.outgoing.ChargePaymentOutPort;
import com.cp.ecommerce.domain.payment.port.outgoing.RefundPaymentOutPort;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

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

    private final RecoveryMetrics recoveryMetrics;

    private final Map<String, ProviderOperationFingerprint> providerOperations = new ConcurrentHashMap<>();

    private final MockPaymentGatewayOperationLedger operationLedger = new MockPaymentGatewayOperationLedger();

    @Value("${payment.gateway.mock.decline-above:10000.00}")
    private BigDecimal declineAboveAmount = new BigDecimal("10000.00");

    @Override
    public String charge(
            final String orderNumber,
            final String operationId,
            final BigDecimal amount,
            final PaymentMethod method) {

        registerProviderOperation(
                operationId,
                new ProviderOperationFingerprint(
                        PaymentProviderOperation.CAPTURE,
                        orderNumber,
                        amount.stripTrailingZeros(),
                        method,
                        null));

        if (amount.compareTo(declineAboveAmount) > 0) {

            throw new PaymentDeclinedException(
                    "Payment gateway declined charge of " + amount + " for order: " + orderNumber + " via " + method);
        }
        final String gatewayReference = resilientExecutor.callResilientOrElse(
                CHARGE_RESILIENCE_INSTANCE_NAME,
                () -> operationLedger.replayCapture(operationId, amount, method),
                exception -> {
                    recoveryMetrics.recordPaymentUnknown();
                    throw new TechnicalProblemException("Could not charge payment for order: " + orderNumber, exception);
                });
        log.info(
                "Mock payment gateway captured {} for order: {} via {} ({}, idempotencyKey={})",
                amount,
                orderNumber,
                method,
                gatewayReference,
                operationId);
        return gatewayReference;
    }

    @Override
    public void refund(
            final String orderNumber,
            final String gatewayReference,
            final String refundId,
            final BigDecimal amount) {

        registerProviderOperation(
                refundId,
                new ProviderOperationFingerprint(
                        PaymentProviderOperation.REFUND,
                        orderNumber,
                        amount.stripTrailingZeros(),
                        null,
                        gatewayReference));

        resilientExecutor.callResilientOrElse(REFUND_RESILIENCE_INSTANCE_NAME, () -> {
            log.info(
                    "Mock payment gateway refunded {} for order: {} ({}, idempotencyKey={})",
                    amount,
                    orderNumber,
                    gatewayReference,
                    refundId);
            return null;
        }, exception -> {
            throw new TechnicalProblemException("Could not refund payment for order: " + orderNumber, exception);
        });
    }

    private void registerProviderOperation(final String operationId, final ProviderOperationFingerprint requested) {

        final ProviderOperationFingerprint existing = providerOperations.putIfAbsent(operationId, requested);
        if (existing != null && !existing.equals(requested)) {
            throw new PaymentOperationConflictException(
                    "Provider operation identity " + operationId + " was reused with different immutable parameters");
        }
    }

    private enum PaymentProviderOperation {
        CAPTURE,
        REFUND
    }

    private record ProviderOperationFingerprint(PaymentProviderOperation operation, String orderNumber, BigDecimal amount,
            PaymentMethod method, String gatewayReference) {
    }

}
