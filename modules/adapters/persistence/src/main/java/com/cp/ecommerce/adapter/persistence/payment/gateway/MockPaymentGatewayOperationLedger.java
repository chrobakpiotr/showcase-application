package com.cp.ecommerce.adapter.persistence.payment.gateway;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

final class MockPaymentGatewayOperationLedger {

    private final Map<String, CaptureOperation> captures = new HashMap<>();

    private final Map<String, Integer> providerMutationCounts = new HashMap<>();

    synchronized String replayCapture(final String operationId, final BigDecimal amount, final PaymentMethod method) {

        final CaptureFingerprint requested = new CaptureFingerprint(amount.stripTrailingZeros(), method);
        final CaptureOperation existing = captures.get(operationId);
        if (existing != null) {

            validateFingerprint(operationId, existing.fingerprint(), requested);
            return existing.gatewayReference();
        }

        final String gatewayReference = "mock-gw-" + operationId;
        captures.put(operationId, new CaptureOperation(requested, gatewayReference));
        providerMutationCounts.merge(operationId, 1, Integer::sum);
        return gatewayReference;
    }

    synchronized void commitCaptureThenLoseResponse(
            final String operationId,
            final BigDecimal amount,
            final PaymentMethod method) {

        replayCapture(operationId, amount, method);
        throw new IllegalStateException("provider response lost after committed capture");
    }

    synchronized int providerMutationCount(final String operationId) {

        return providerMutationCounts.getOrDefault(operationId, 0);
    }

    private static void validateFingerprint(
            final String operationId,
            final CaptureFingerprint existing,
            final CaptureFingerprint requested) {

        if (!existing.equals(requested)) {

            throw new PaymentOperationConflictException(
                    "Provider operation identity " + operationId + " was reused with different immutable parameters");
        }
    }

    private record CaptureFingerprint(BigDecimal amount, PaymentMethod method) {
    }

    private record CaptureOperation(CaptureFingerprint fingerprint, String gatewayReference) {
    }
}
