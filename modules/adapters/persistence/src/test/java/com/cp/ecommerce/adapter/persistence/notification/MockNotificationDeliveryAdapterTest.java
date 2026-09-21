package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link MockNotificationDeliveryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class MockNotificationDeliveryAdapterTest {

    @Mock
    private transient ResilientExecutor resilientExecutor;

    @InjectMocks
    private transient MockNotificationDeliveryAdapter mockNotificationDeliveryAdapter;

    @Test
    void shouldDeliverNotification() {

        org.mockito.Mockito.when(resilientExecutor.callResilientOrElse(anyString(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });

        mockNotificationDeliveryAdapter.deliver(NotificationBuilder.mockNotification());

        verify(resilientExecutor).callResilientOrElse(anyString(), any(), any());
    }

    @Test
    void shouldWrapUnexpectedDeliveryFailureAsTechnicalProblem() {

        org.mockito.Mockito.when(resilientExecutor.callResilientOrElse(anyString(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Function<RuntimeException, ?> fallback = invocation.getArgument(2);
            return fallback.apply(new IllegalStateException("gateway timeout"));
        });

        assertThatThrownBy(() -> mockNotificationDeliveryAdapter.deliver(NotificationBuilder.mockNotification()))
                .isInstanceOf(TechnicalProblemException.class);
    }

}
