package com.cp.ecommerce.domain.order;

/** Ownership token for one autonomous cancellation recovery attempt. */
public record OrderCancellationRecoveryClaim(String orderNumber, String claimId) {
}
