package com.cp.ecommerce.application.order;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRedriveOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationBadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RedriveOrderCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");

    @Mock
    private ManageOrderCancellationRedriveOutPort redriveOutPort;

    private RedriveOrderCancellationService service;

    @BeforeEach
    void setUp() {
        service = new RedriveOrderCancellationService(redriveOutPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldValidateAndForwardCommandWithControlledTime() {

        final OrderCancellationRedriveCommand command = new OrderCancellationRedriveCommand(
                "cmd-1",
                "ORDER-1",
                "operator-a",
                "incident-456");
        given(redriveOutPort.redrive(command, NOW)).willReturn(OrderCancellationRedriveOutcome.REQUEUED);

        assertThat(service.redrive(command)).isEqualTo(OrderCancellationRedriveOutcome.REQUEUED);

        verify(redriveOutPort).redrive(command, NOW);
    }

    @Test
    void shouldRejectBlankRequiredValueBeforePersistence() {

        final OrderCancellationRedriveCommand command = new OrderCancellationRedriveCommand(
                " ",
                "ORDER-1",
                "operator-a",
                "incident-456");

        assertThatThrownBy(() -> service.redrive(command)).isInstanceOf(ApplicationBadRequestException.class)
                .hasMessageContaining("commandId is required");

        verifyNoInteractions(redriveOutPort);
    }

    @Test
    void shouldRejectOversizedValueBeforePersistence() {

        final OrderCancellationRedriveCommand command = new OrderCancellationRedriveCommand(
                "cmd-1",
                "O".repeat(41),
                "operator-a",
                "incident-456");

        assertThatThrownBy(() -> service.redrive(command)).isInstanceOf(ApplicationBadRequestException.class)
                .hasMessageContaining("orderNumber must not exceed 40 characters");

        verifyNoInteractions(redriveOutPort);
    }
}
