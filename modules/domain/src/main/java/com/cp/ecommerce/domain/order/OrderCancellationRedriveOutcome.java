package com.cp.ecommerce.domain.order;

/** Outcome of accepting an operator cancellation-redrive command. */
public enum OrderCancellationRedriveOutcome {

    REQUEUED,
    REPLAYED
}
