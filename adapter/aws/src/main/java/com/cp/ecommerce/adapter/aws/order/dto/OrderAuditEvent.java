package com.cp.ecommerce.adapter.aws.order.dto;

import lombok.Builder;

/**
 * Adapter-local DTO representing a lightweight order audit event sent to SQS. This is intentionally kept separate from the
 * domain {@code Order} object.
 *
 * @param timestamp ISO-8601 timestamp string, e.g. {@code 2024-01-01T00:00:00Z}.
 */
@Builder
public record OrderAuditEvent(String orderNumber, Long customerId, String eventType, String timestamp) {

}
