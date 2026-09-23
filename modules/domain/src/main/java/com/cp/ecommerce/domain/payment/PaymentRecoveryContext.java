package com.cp.ecommerce.domain.payment;

/**
 * Ownership identity carried only by payment-reconciliation recovery workers.
 *
 * @param operationId stable provider operation identity
 * @param claimId current reconciliation claim identity
 */
public record PaymentRecoveryContext(String operationId, String claimId) {
}
