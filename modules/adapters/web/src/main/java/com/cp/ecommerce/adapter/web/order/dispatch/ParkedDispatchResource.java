package com.cp.ecommerce.adapter.web.order.dispatch;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ParkedDispatchResource(String dispatchId, String orderNumber, String dispatchType,
        String status, int attempts, Instant createdAt, Instant nextAttemptAt, String reasonCode) {
}
