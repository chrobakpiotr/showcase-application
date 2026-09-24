package com.cp.ecommerce.application.order;

import java.time.Clock;
import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.port.incoming.RedriveOrderCancellationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRedriveOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Validates and submits operator cancellation-redrive commands to the durable persistence boundary. */
@Service
@RequiredArgsConstructor
public class RedriveOrderCancellationService implements RedriveOrderCancellationInPort {

    private static final int COMMAND_ID_MAX_LENGTH = 80;
    private static final int ORDER_NUMBER_MAX_LENGTH = 40;
    private static final int ACTOR_MAX_LENGTH = 120;
    private static final int REASON_MAX_LENGTH = 500;

    private final ManageOrderCancellationRedriveOutPort redriveOutPort;
    private final Clock clock;

    @Override
    public OrderCancellationRedriveOutcome redrive(final OrderCancellationRedriveCommand command) {

        requireText(command.commandId(), "commandId", COMMAND_ID_MAX_LENGTH);
        requireText(command.orderNumber(), "orderNumber", ORDER_NUMBER_MAX_LENGTH);
        requireText(command.actor(), "actor", ACTOR_MAX_LENGTH);
        requireText(command.reason(), "reason", REASON_MAX_LENGTH);
        return redriveOutPort.redrive(command, Instant.ofEpochMilli(clock.instant().toEpochMilli()));
    }

    private static void requireText(final String value, final String field, final int maxLength) {

        if (value == null || value.isBlank()) {
            throw new ApplicationBadRequestException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new ApplicationBadRequestException(field + " must not exceed " + maxLength + " characters");
        }
    }
}
