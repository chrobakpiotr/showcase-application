package com.cp.ecommerce.domain.order;

/** Immutable operator command for requeueing a cancellation parked in manual review. */
public record OrderCancellationRedriveCommand(String commandId, String orderNumber, String actor, String reason) {
}
