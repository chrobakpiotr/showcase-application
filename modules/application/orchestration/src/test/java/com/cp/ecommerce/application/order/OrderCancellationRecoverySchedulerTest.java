package com.cp.ecommerce.application.order;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRecoveryOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancellationRecoverySchedulerTest {

    private static final String ORDER_1 = "ORDER-1";
    private static final String ORDER_2 = "ORDER-2";

    private static final String CLAIM_1 = "claim-1";

    private static final String CLAIM_2 = "claim-2";

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    @Mock
    private ManageOrderCancellationRecoveryOutPort recoveryOutPort;
    @Mock
    private CancelOrderWorkflow cancelOrderWorkflow;

    @Test
    void shouldReleaseWaitingClaimWithoutRecordingSuccessOrFailure() {

        given(recoveryOutPort.findDueCancellationOrderNumbers(NOW, 50)).willReturn(List.of(ORDER_1));
        given(recoveryOutPort.claim(ORDER_1, NOW)).willReturn(new OrderCancellationRecoveryClaim(ORDER_1, CLAIM_1));
        given(cancelOrderWorkflow.recoverCancellation(ORDER_1, CLAIM_1))
                .willReturn(CancellationRecoveryOutcome.WAITING_FOR_REFUND);

        new OrderCancellationRecoveryScheduler(recoveryOutPort, cancelOrderWorkflow, Clock.fixed(NOW, ZoneOffset.UTC))
                .recover();

        verify(recoveryOutPort).recordWaiting(ORDER_1, CLAIM_1, NOW);
        verify(recoveryOutPort, org.mockito.Mockito.never()).recordSuccess(ORDER_1, CLAIM_1);
        verify(recoveryOutPort, org.mockito.Mockito.never()).recordFailure(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any());
    }

    @Test
    void shouldIgnoreLostClaimWithoutRecordingSuccessOrFailure() {

        given(recoveryOutPort.findDueCancellationOrderNumbers(NOW, 50)).willReturn(List.of(ORDER_1));
        given(recoveryOutPort.claim(ORDER_1, NOW)).willReturn(new OrderCancellationRecoveryClaim(ORDER_1, CLAIM_1));
        given(cancelOrderWorkflow.recoverCancellation(ORDER_1, CLAIM_1)).willReturn(CancellationRecoveryOutcome.LOST_CLAIM);

        new OrderCancellationRecoveryScheduler(recoveryOutPort, cancelOrderWorkflow, Clock.fixed(NOW, ZoneOffset.UTC))
                .recover();

        verify(recoveryOutPort, org.mockito.Mockito.never()).recordSuccess(ORDER_1, CLAIM_1);
        verify(recoveryOutPort, org.mockito.Mockito.never()).recordFailure(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any());
    }

    @Test
    void shouldClaimFenceAndContinueAfterOneRecoveryFails() {
        given(recoveryOutPort.findDueCancellationOrderNumbers(NOW, 50)).willReturn(List.of(ORDER_1, ORDER_2, "ORDER-3"));
        given(recoveryOutPort.claim(ORDER_1, NOW)).willReturn(new OrderCancellationRecoveryClaim(ORDER_1, CLAIM_1));
        given(recoveryOutPort.claim(ORDER_2, NOW)).willReturn(new OrderCancellationRecoveryClaim(ORDER_2, CLAIM_2));
        given(recoveryOutPort.claim("ORDER-3", NOW)).willReturn(null);
        doThrow(new IllegalStateException("temporary failure")).when(cancelOrderWorkflow).recoverCancellation(ORDER_1, CLAIM_1);

        given(cancelOrderWorkflow.recoverCancellation(ORDER_2, CLAIM_2)).willReturn(CancellationRecoveryOutcome.COMPLETED);

        new OrderCancellationRecoveryScheduler(recoveryOutPort, cancelOrderWorkflow, Clock.fixed(NOW, ZoneOffset.UTC))
                .recover();

        verify(recoveryOutPort).recordFailure(ORDER_1, CLAIM_1, "temporary failure", NOW);
        verify(cancelOrderWorkflow).recoverCancellation(ORDER_2, CLAIM_2);
        verify(recoveryOutPort).recordSuccess(ORDER_2, CLAIM_2);
    }
}
