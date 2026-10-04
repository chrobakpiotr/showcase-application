package com.cp.ecommerce.adapter.web.order.dispatch;

import java.util.Optional;

import com.cp.ecommerce.adapter.security.authentication.CurrentOperatorProvider;
import com.cp.ecommerce.adapter.web.exception.GlobalExceptionHandler;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveCommand;
import com.cp.ecommerce.domain.order.dispatch.DispatchRedriveOutcome;
import com.cp.ecommerce.domain.order.dispatch.port.incoming.RedriveDispatchInPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;
import com.cp.ecommerce.foundation.exception.OrderPlacementDispatchRedriveConflictException;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.micrometer.tracing.Tracer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispatchRedriveControllerTest {
    private static final String PATH = "/api/order-placement/dispatches/dispatch/redrive";

    @Test
    void shouldUseAuthenticatedActorAndReturnAcceptedForRequeueAndReplay() throws Exception {
        final var port = mock(RedriveDispatchInPort.class);
        final var actor = mock(CurrentOperatorProvider.class);
        when(actor.currentOperator()).thenReturn(Optional.of("operator"));
        for (final var outcome : DispatchRedriveOutcome.values()) {
            when(port.redrive(new DispatchRedriveCommand("cmd", "dispatch", "operator", "inspected"))).thenReturn(outcome);
            MockMvcBuilders.standaloneSetup(new DispatchRedriveController(port, actor)).build()
                    .perform(post(PATH).header("X-Redrive-Command-Id", "cmd").header("X-Redrive-Reason", " inspected ")
                            .header("X-Actor", "caller-forged"))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.outcome").value(outcome.name()))
                    .andExpect(jsonPath("$.commandId").value("cmd"))
                    .andExpect(jsonPath("$.actor").doesNotExist()).andExpect(jsonPath("$.reason").doesNotExist());
        }
    }

    @Test
    void shouldRejectMissingHeadersInvalidInputAndAbsentActorBeforeCallingPort() throws Exception {
        final var port = mock(RedriveDispatchInPort.class);
        final var actor = mock(CurrentOperatorProvider.class);
        when(actor.currentOperator()).thenReturn(Optional.of("operator"));
        final var mvc = MockMvcBuilders.standaloneSetup(new DispatchRedriveController(port, actor)).build();
        mvc.perform(post(PATH)).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).header("X-Redrive-Command-Id", "cmd").header("X-Redrive-Reason", " "))
                .andExpect(status().isBadRequest());
        when(actor.currentOperator()).thenReturn(Optional.empty());
        mvc.perform(post(PATH).header("X-Redrive-Command-Id", "cmd").header("X-Redrive-Reason", "reason"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(port);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveGlobalNotFoundAndConflictProblemResponses() throws Exception {
        final var port = mock(RedriveDispatchInPort.class);
        final var actor = mock(CurrentOperatorProvider.class);
        when(actor.currentOperator()).thenReturn(Optional.of("operator"));
        final var mvc = MockMvcBuilders.standaloneSetup(new DispatchRedriveController(port, actor))
                .setControllerAdvice(new GlobalExceptionHandler((ObjectProvider<Tracer>) mock(ObjectProvider.class))).build();
        final var command = new DispatchRedriveCommand("cmd", "dispatch", "operator", "reason");
        when(port.redrive(command)).thenThrow(new ApplicationNotFoundException("not found"));
        mvc.perform(post(PATH).header("X-Redrive-Command-Id", "cmd").header("X-Redrive-Reason", "reason"))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.doThrow(new OrderPlacementDispatchRedriveConflictException("ineligible")).when(port).redrive(command);
        mvc.perform(post(PATH).header("X-Redrive-Command-Id", "cmd").header("X-Redrive-Reason", "reason"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").isNotEmpty());
    }
}
