package com.cp.ecommerce.adapter.web.order.dispatch;

import com.cp.ecommerce.adapter.security.authentication.CurrentOperatorProvider;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.port.incoming.RedriveDispatchInPort;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DispatchRedriveController {

    private final RedriveDispatchInPort port;
    private final CurrentOperatorProvider operator;

    @PostMapping("/api/order-placement/dispatches/{dispatchId}/redrive")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DispatchRedriveResource redrive(
            @PathVariable("dispatchId") final String dispatchId,
            @RequestHeader("X-Redrive-Command-Id") final String commandId,
            @RequestHeader("X-Redrive-Reason") final String reason) {
        final String actor = operator.currentOperator()
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Authenticated operator identity is required for dispatch redrive"));
        final DispatchRedriveCommand command;
        try {
            command = new DispatchRedriveCommand(commandId, dispatchId, actor, reason);
        } catch (final IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        return new DispatchRedriveResource(command.commandId(), command.dispatchId(), port.redrive(command).name());
    }
}
