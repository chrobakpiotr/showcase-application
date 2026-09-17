package com.cp.ecommerce.domain.notification;

import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Notification}.
 */
class NotificationTest {

    @Test
    void shouldPassValidationForValidNotification() {

        final Notification notification = TestDomainObjectFactory.validNotification();

        assertDoesNotThrow(notification::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenRecipientEmailIsInvalid() {

        final Notification notification = Notification.builder()
                .notificationId(TestDomainObjectFactory.TEST_NOTIFICATION_ID)
                .recipientEmail("invalid")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Body")
                .createdDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, notification::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenSubjectIsTooLong() {

        final Notification notification = Notification.builder()
                .notificationId(TestDomainObjectFactory.TEST_NOTIFICATION_ID)
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("x".repeat(ValidationConstants.NOTIFICATION_SUBJECT_MAX + 1))
                .body("Body")
                .createdDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, notification::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenBodyIsBlank() {

        final Notification notification = Notification.builder()
                .notificationId(TestDomainObjectFactory.TEST_NOTIFICATION_ID)
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body(" ")
                .createdDate(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, notification::assertValidationsEmpty);
    }

}
