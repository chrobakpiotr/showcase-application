package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;

/** Operator-only cancellation recovery command boundary. */
public interface RedriveOrderCancellationInPort {

    OrderCancellationRedriveOutcome redrive(OrderCancellationRedriveCommand command);
}
