package com.cp.ecommerce.application.order;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationFencedFinalizationRedTest {

    @Test
    void recoveryWorkflowShouldCarryClaimIdentityIntoCancellationExecution() throws Exception {

        final Method method = CancelOrderWorkflow.class.getMethod("cancelOrder", String.class, String.class);

        assertThat(method.getReturnType().getName()).isEqualTo("com.cp.ecommerce.domain.order.Order");
    }

    @Test
    void cancellationFinalizationShouldRequireClaimIdentity() throws Exception {

        final Method method = OrderCancellationArbitrator.class
                .getDeclaredMethod("completeCancellation", String.class, String.class);

        assertThat(method.getReturnType()).isEqualTo(void.class);
    }
}
