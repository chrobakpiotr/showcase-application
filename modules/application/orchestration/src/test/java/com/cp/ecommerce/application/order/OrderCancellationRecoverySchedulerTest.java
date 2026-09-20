package com.cp.ecommerce.application.order;

import java.util.List;

import com.cp.ecommerce.domain.order.port.outgoing.FindOrderCancellationCandidatesOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancellationRecoverySchedulerTest {

    @Mock
    private FindOrderCancellationCandidatesOutPort candidatesOutPort;

    @Mock
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Test
    void shouldContinueRecoveryAfterOneCandidateFails() {

        given(candidatesOutPort.findCancellationCandidates(50)).willReturn(List.of("ORDER-1", "ORDER-2"));
        doThrow(new IllegalStateException("temporary failure")).when(cancelOrderWorkflow).cancelOrder("ORDER-1");

        new OrderCancellationRecoveryScheduler(candidatesOutPort, cancelOrderWorkflow).recover();

        verify(cancelOrderWorkflow).cancelOrder("ORDER-1");
        verify(cancelOrderWorkflow).cancelOrder("ORDER-2");
    }
}
