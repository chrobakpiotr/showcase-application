package com.cp.ecommerce.domain.order.dispatch.port.outgoing;

import java.time.Instant;

import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;

public interface RedriveDispatchOutPort {
    DispatchRedriveOutcome redrive(DispatchRedriveCommand command, Instant now);
}
