package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.domain.notification.port.incoming.RetryNotificationDeliveryInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private transient RetryNotificationDeliveryInPort retryNotificationDeliveryInPort;

    @Test
    void shouldDelegateScheduledRetry() {

        new NotificationRetryScheduler(retryNotificationDeliveryInPort).retryDueNotifications();

        verify(retryNotificationDeliveryInPort).retryDueNotifications();
    }
}
