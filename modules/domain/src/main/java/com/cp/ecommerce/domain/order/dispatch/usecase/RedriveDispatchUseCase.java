package com.cp.ecommerce.domain.order.dispatch.usecase;

import java.time.Clock;

import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;
import com.cp.ecommerce.domain.order.dispatch.port.incoming.RedriveDispatchInPort;
import com.cp.ecommerce.domain.order.dispatch.port.outgoing.RedriveDispatchOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class RedriveDispatchUseCase implements RedriveDispatchInPort {

    private final RedriveDispatchOutPort port;
    private final Clock clock;

    @Override
    public DispatchRedriveOutcome redrive(final DispatchRedriveCommand command) {
        return port.redrive(command, java.time.Instant.ofEpochMilli(clock.instant().toEpochMilli()));
    }
}
