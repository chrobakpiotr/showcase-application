package com.cp.ecommerce.application.order;

import java.lang.reflect.Method;

import com.cp.ecommerce.domain.order.CancellationCompletionOutcome;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationFencedFinalizationRedTest {

    @Test
    void recoveryWorkflowShouldCarryClaimIdentityIntoCancellationExecution() throws Exception {
        final Method method = CancelOrderWorkflow.class.getMethod("cancelOrder", String.class, String.class);
        assertThat(method.getReturnType().getName()).isEqualTo("com.cp.ecommerce.domain.order.Order");
    }

    @Test
    void cancellationFinalizationMustReturnAnExplicitFencedOutcome() throws Exception {
        final Method method = OrderCancellationArbitrator.class
                .getDeclaredMethod("completeCancellation", String.class, String.class);
        assertThat(method.getReturnType()).isEqualTo(CancellationCompletionOutcome.class);
    }

    @Test
    void atomicFinalizationMustAcceptTheTerminalActionInsideItsTransaction() throws Exception {
        final Method method = OrderCancellationArbitrator.class
                .getDeclaredMethod("finalizeCancellation", String.class, String.class, Runnable.class);
        assertThat(method.getReturnType()).isEqualTo(CancellationCompletionOutcome.class);
    }
}
