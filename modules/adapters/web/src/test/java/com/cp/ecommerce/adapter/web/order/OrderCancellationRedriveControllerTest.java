package com.cp.ecommerce.adapter.web.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.security.authentication.CurrentOperatorProvider;
import com.cp.ecommerce.adapter.web.order.resource.OrderCancellationRedriveResource;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveCommand;
import com.cp.ecommerce.domain.order.OrderCancellationRedriveOutcome;
import com.cp.ecommerce.domain.order.port.incoming.RedriveOrderCancellationInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderCancellationRedriveControllerTest {

    @Mock
    private RedriveOrderCancellationInPort redriveOrderCancellationInPort;

    @Mock
    private CurrentOperatorProvider currentOperatorProvider;

    @InjectMocks
    private OrderCancellationRedriveController controller;

    @Test
    void shouldForwardAuthenticatedActorReasonAndStableCommandId() {

        given(currentOperatorProvider.currentOperator()).willReturn(Optional.of("operator-a"));
        given(redriveOrderCancellationInPort.redrive(any())).willReturn(OrderCancellationRedriveOutcome.REQUEUED);

        final OrderCancellationRedriveResource response = controller.redrive("ORDER-123", "cmd-001", "incident-456");

        final ArgumentCaptor<OrderCancellationRedriveCommand> command = ArgumentCaptor
                .forClass(OrderCancellationRedriveCommand.class);
        verify(redriveOrderCancellationInPort).redrive(command.capture());

        assertThat(command.getValue().commandId()).isEqualTo("cmd-001");
        assertThat(command.getValue().orderNumber()).isEqualTo("ORDER-123");
        assertThat(command.getValue().actor()).isEqualTo("operator-a");
        assertThat(command.getValue().reason()).isEqualTo("incident-456");
        assertThat(response.status()).isEqualTo("REQUEUED");
    }

    @Test
    void shouldFailClosedWhenAuthenticatedPrincipalHasNoOperatorIdentity() {

        given(currentOperatorProvider.currentOperator()).willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.redrive("ORDER-123", "cmd-001", "incident-456")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(redriveOrderCancellationInPort);
    }
}
