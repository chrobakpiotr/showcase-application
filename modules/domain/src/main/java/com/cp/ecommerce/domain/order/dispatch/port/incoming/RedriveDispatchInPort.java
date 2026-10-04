package com.cp.ecommerce.domain.order.dispatch.port.incoming;

import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;

public interface RedriveDispatchInPort {

    DispatchRedriveOutcome redrive(DispatchRedriveCommand command);
}
