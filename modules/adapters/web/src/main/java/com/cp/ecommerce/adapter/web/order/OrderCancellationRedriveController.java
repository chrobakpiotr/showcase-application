package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.adapter.security.authentication.CurrentOperatorProvider;
import com.cp.ecommerce.adapter.web.order.resource.OrderCancellationRedriveResource;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.port.incoming.RedriveOrderCancellationInPort;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Operator-only HTTP boundary for durable cancellation MANUAL_REVIEW redrive commands. */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderCancellationRedriveController {

    static final String COMMAND_ID_HEADER = "X-Redrive-Command-Id";
    static final String REASON_HEADER = "X-Redrive-Reason";

    private final RedriveOrderCancellationInPort redriveOrderCancellationInPort;
    private final CurrentOperatorProvider currentOperatorProvider;

    @PostMapping("/{orderNumber}/cancellation-redrive")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderCancellationRedriveResource redrive(
            @PathVariable("orderNumber") final String orderNumber,
            @RequestHeader(COMMAND_ID_HEADER) final String commandId,
            @RequestHeader(REASON_HEADER) final String reason) {

        final String actor = currentOperatorProvider.currentOperator()
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Authenticated operator identity is required for cancellation redrive"));

        final OrderCancellationRedriveOutcome outcome = redriveOrderCancellationInPort
                .redrive(new OrderCancellationRedriveCommand(commandId, orderNumber, actor, reason));
        return new OrderCancellationRedriveResource(commandId, orderNumber, outcome.name());
    }
}
