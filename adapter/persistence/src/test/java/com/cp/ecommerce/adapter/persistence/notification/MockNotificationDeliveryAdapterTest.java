package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));

        mockNotificationDeliveryAdapter.deliver(NotificationBuilder.mockNotification());

        verify(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

    @Test
    void shouldWrapUnexpectedDeliveryFailureAsTechnicalProblem() {

        doThrow(new RuntimeException("gateway timeout")).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));

        assertThatThrownBy(() -> mockNotificationDeliveryAdapter.deliver(NotificationBuilder.mockNotification()))
                .isInstanceOf(TechnicalProblemException.class);
    }

}
