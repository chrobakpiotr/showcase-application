package com.cp.ecommerce.domain.order.port.outgoing;

import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;

/** Durable audit and fenced state-transition boundary for cancellation manual-review redrive. */
public interface ManageOrderCancellationRedriveOutPort {

    OrderCancellationRedriveOutcome redrive(OrderCancellationRedriveCommand command, Instant now);
}
