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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispatchRedriveControllerTest {

    private static final String PATH = "/api/order-placement/dispatches/dispatch/redrive";
    private static final String COMMAND_ID = "cmd";
    private static final String DISPATCH_ID = "dispatch";
    private static final String OPERATOR_ID = "operator";
    private static final String REASON = "reason";
    private static final String COMMAND_ID_HEADER = "X-Redrive-Command-Id";
    private static final String REASON_HEADER = "X-Redrive-Reason";

    @Test
    void shouldUseAuthenticatedActorAndReturnAcceptedForRequeueAndReplay() throws Exception {
        final var port = mock(RedriveDispatchInPort.class);
        final var actor = mock(CurrentOperatorProvider.class);
        when(actor.currentOperator()).thenReturn(Optional.of(OPERATOR_ID));
        for (final var outcome : DispatchRedriveOutcome.values()) {
            when(port.redrive(new DispatchRedriveCommand(COMMAND_ID, DISPATCH_ID, OPERATOR_ID, "inspected")))
                    .thenReturn(outcome);
            MockMvcBuilders.standaloneSetup(new DispatchRedriveController(port, actor))
                    .build()
                    .perform(
                            post(PATH).header(COMMAND_ID_HEADER, COMMAND_ID)
                                    .header(REASON_HEADER, " inspected ")
                                    .header("X-Actor", "caller-forged"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.outcome").value(outcome.name()))
                    .andExpect(jsonPath("$.commandId").value(COMMAND_ID))
                    .andExpect(jsonPath("$.actor").doesNotExist())
                    .andExpect(jsonPath("$.reason").doesNotExist());
        }
    }

    @Test
    void shouldRejectMissingHeadersInvalidInputAndAbsentActorBeforeCallingPort() throws Exception {
        final var port = mock(RedriveDispatchInPort.class);
        final var actor = mock(CurrentOperatorProvider.class);
        when(actor.currentOperator()).thenReturn(Optional.of(OPERATOR_ID));
        final var mvc = MockMvcBuilders.standaloneSetup(new DispatchRedriveController(port, actor)).build();
        mvc.perform(post(PATH)).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).header(COMMAND_ID_HEADER, COMMAND_ID).header(REASON_HEADER, " "))
                .andExpect(status().isBadRequest());
        when(actor.currentOperator()).thenReturn(Optional.empty());
        mvc.perform(post(PATH).header(COMMAND_ID_HEADER, COMMAND_ID).header(REASON_HEADER, REASON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(port);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveGlobalNotFoundAndConflictProblemResponses() throws Exception {
        final var port = mock(RedriveDispatchInPort.class);
        final var actor = mock(CurrentOperatorProvider.class);
        when(actor.currentOperator()).thenReturn(Optional.of(OPERATOR_ID));
        final var mvc = MockMvcBuilders.standaloneSetup(new DispatchRedriveController(port, actor))
                .setControllerAdvice(new GlobalExceptionHandler((ObjectProvider<Tracer>) mock(ObjectProvider.class)))
                .build();
        final var command = new DispatchRedriveCommand(COMMAND_ID, DISPATCH_ID, OPERATOR_ID, REASON);
        when(port.redrive(command)).thenThrow(new ApplicationNotFoundException("not found"));
        mvc.perform(post(PATH).header(COMMAND_ID_HEADER, COMMAND_ID).header(REASON_HEADER, REASON))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.doThrow(new OrderPlacementDispatchRedriveConflictException("ineligible"))
                .when(port)
                .redrive(command);
        mvc.perform(post(PATH).header(COMMAND_ID_HEADER, COMMAND_ID).header(REASON_HEADER, REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorId").isNotEmpty());
    }
}
