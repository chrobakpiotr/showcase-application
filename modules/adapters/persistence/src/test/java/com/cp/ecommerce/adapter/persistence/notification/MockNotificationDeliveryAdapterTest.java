package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.adapter.common.utils.LogCapture;
import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_BODY;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_NOTIFICATION_ID;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_RECIPIENT_EMAIL;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_SUBJECT;

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

        final String operationId = "PRIVATE_IDEMPOTENCY_KEY";
        try (LogCapture logs = new LogCapture(MockNotificationDeliveryAdapter.class)) {
            mockNotificationDeliveryAdapter.deliver(operationId, NotificationBuilder.mockNotification());
            assertThat(logs.formattedMessages())
                    .contains("Mock notification delivery outcome=RECORDED channel=EMAIL")
                    .doesNotContain(
                            operationId,
                            TEST_NOTIFICATION_ID,
                            TEST_RECIPIENT_EMAIL,
                            TEST_SUBJECT,
                            TEST_BODY);
        }

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
