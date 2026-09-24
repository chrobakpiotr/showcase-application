package com.cp.ecommerce.adapter.web.order.resource;

/** Response returned after accepting or replaying a durable cancellation-redrive command. */
public record OrderCancellationRedriveResource(String commandId, String orderNumber, String status) {
}
