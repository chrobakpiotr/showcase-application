package com.cp.ecommerce.domain.order.dispatch;

import java.time.Instant;

public record ParkedDispatch(String dispatchId, String orderNumber, String dispatchType,
        int attempts, Instant createdAt, ReasonCode reasonCode) {
    public enum ReasonCode {
        ORDER_MISSING,
        ATTEMPT_BUDGET_EXHAUSTED,
        OTHER
    }
}
