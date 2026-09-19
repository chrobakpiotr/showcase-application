package com.cp.ecommerce.adapter.common.utils;

import java.time.Instant;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Notification} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationBuilder {

    public static final String TEST_NOTIFICATION_ID = "NOTIF-1234";

    public static final String TEST_RECIPIENT_EMAIL = "customer@example.com";

    public static final NotificationChannel TEST_CHANNEL = NotificationChannel.EMAIL;

    public static final NotificationType TEST_TYPE = NotificationType.ORDER_CONFIRMED;

    public static final String TEST_SUBJECT = "Order ORD-1001 confirmed";

    public static final String TEST_BODY = "Your order ORD-1001 was confirmed.";

    public static final NotificationStatus TEST_STATUS = NotificationStatus.SENT;

    public static final Instant TEST_CREATED_DATE = Instant.ofEpochMilli(1710000000000L);

    public static final Instant TEST_SENT_DATE = Instant.ofEpochMilli(1710003600000L);

    public static Notification mockNotification() {

        return Notification.builder()
                .notificationId(TEST_NOTIFICATION_ID)
                .recipientEmail(TEST_RECIPIENT_EMAIL)
                .channel(TEST_CHANNEL)
                .type(TEST_TYPE)
                .subject(TEST_SUBJECT)
                .body(TEST_BODY)
                .status(TEST_STATUS)
                .createdDate(TEST_CREATED_DATE)
                .sentDate(TEST_SENT_DATE)
                .build();
    }

}
