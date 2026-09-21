package com.cp.ecommerce.application.order;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationWaitingOutcomeRedTest {

    @Test
    void recoveryCancellationShouldExposeDurableOutcomeInsteadOfReturningOnlyOrder() throws Exception {

        final Method method = CancelOrderWorkflow.class.getMethod("recoverCancellation", String.class, String.class);

        assertThat(method.getReturnType().getSimpleName()).isEqualTo("CancellationRecoveryOutcome");
    }

    @Test
    void recoveryOutcomeShouldDistinguishCompletedFromWaitingForRefund() throws Exception {

        final Class<?> outcome = Class.forName("com.cp.ecommerce.application.order.CancellationRecoveryOutcome");

        assertThat(outcome.isEnum()).isTrue();

        final Object[] values = outcome.getEnumConstants();
        assertThat(values).extracting(Object::toString).contains("COMPLETED", "WAITING_FOR_REFUND");
    }
}
