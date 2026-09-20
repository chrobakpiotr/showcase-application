package com.cp.ecommerce.domain.order.port.outgoing;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;

/** Durable multi-replica lease/fencing boundary for autonomous CANCELLING recovery. */
public interface ManageOrderCancellationRecoveryOutPort {

    List<String> findDueCancellationOrderNumbers(Instant now, int limit);

    OrderCancellationRecoveryClaim claim(String orderNumber, Instant now);

    void recordSuccess(String orderNumber, String claimId);

    void recordFailure(String orderNumber, String claimId, String error, Instant failedAt);
}
