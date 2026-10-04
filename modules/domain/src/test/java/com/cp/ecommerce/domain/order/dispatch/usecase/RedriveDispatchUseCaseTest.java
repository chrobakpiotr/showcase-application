package com.cp.ecommerce.domain.order.dispatch.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;
import com.cp.ecommerce.domain.order.dispatch.port.outgoing.RedriveDispatchOutPort;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedriveDispatchUseCaseTest {
    @Test
    void shouldBindValidatedCommandAndInjectedTime() {
        final var command = new DispatchRedriveCommand("command", "dispatch", "operator", " inspected ");
        assertThat(command.reason()).isEqualTo("inspected");
        final var now = Instant.parse("2026-10-04T12:00:00Z");
        final var port = mock(RedriveDispatchOutPort.class);
        when(port.redrive(command, now)).thenReturn(DispatchRedriveOutcome.REQUEUED);
        assertThat(new RedriveDispatchUseCase(port, Clock.fixed(now, ZoneOffset.UTC)).redrive(command))
                .isEqualTo(DispatchRedriveOutcome.REQUEUED);
    }

    @Test
    void shouldRejectInvalidCommandShapes() {
        for (final String id : new String[] { "", " ", "a".repeat(81) }) {
            assertThatThrownBy(() -> new DispatchRedriveCommand(id, "dispatch", "operator", "reason"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (final String reason : new String[] { " ", "a".repeat(501) }) {
            assertThatThrownBy(() -> new DispatchRedriveCommand("command", "dispatch", "operator", reason))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new DispatchRedriveCommand("command", "a".repeat(101), "operator", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DispatchRedriveCommand("command", "dispatch", "", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
