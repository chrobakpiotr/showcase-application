package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_BODY;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_CHANNEL;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_CREATED_DATE;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_NOTIFICATION_ID;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_RECIPIENT_EMAIL;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_SENT_DATE;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_STATUS;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_SUBJECT;
import static com.cp.ecommerce.adapter.common.utils.NotificationBuilder.TEST_TYPE;

/**
 * Builder class for {@link NotificationEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationEntityBuilder {

    public static NotificationEntity mockNotificationEntity() {

        return NotificationEntity.builder()
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
