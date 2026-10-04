package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;

public interface ParkedDispatchProjection {
    String getDispatchId();
    String getOrderNumber();
    OrderPlacementDispatchType getDispatchType();
    int getAttempts();
    Instant getCreatedAt();
    String getReasonCode();
}
