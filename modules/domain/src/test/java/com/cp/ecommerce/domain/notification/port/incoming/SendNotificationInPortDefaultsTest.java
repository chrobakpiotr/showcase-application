package com.cp.ecommerce.domain.notification.port.incoming;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendNotificationInPortDefaultsTest {

    @Test
    void compatibilityHelperShouldGenerateStableLegacyKeyAndReturnDelegateResult() {

        final RecordingSendNotificationPort port = new RecordingSendNotificationPort();
        final Notification expected = Notification.builder()
                .notificationId("N-1")
                .eventKey("persisted-event")
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("subject")
                .body("body")
                .createdDate(java.time.Instant.parse("2026-09-21T10:00:00Z"))
                .build();
        port.result = expected;

        final Notification actual = port
                .sendNotification("john.doe@test.com", NotificationType.ORDER_CONFIRMED, "subject", "body");

        assertThat(actual).isSameAs(expected);
        assertThat(port.eventKey).startsWith("legacy:");
        assertThat(port.recipientEmail).isEqualTo("john.doe@test.com");
        assertThat(port.type).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(port.subject).isEqualTo("subject");
        assertThat(port.body).isEqualTo("body");

        final String firstEventKey = port.eventKey;

        port.sendNotification("john.doe@test.com", NotificationType.ORDER_CONFIRMED, "subject", "body");

        assertThat(port.eventKey).isEqualTo(firstEventKey);
    }

    private static final class RecordingSendNotificationPort implements SendNotificationInPort {

        private Notification result;
        private String eventKey;
        private String recipientEmail;
        private NotificationType type;
        private String subject;
        private String body;

        @Override
        public Notification sendNotification(
                final String eventKey,
                final String recipientEmail,
                final NotificationType type,
                final String subject,
                final String body) {

            this.eventKey = eventKey;
            this.recipientEmail = recipientEmail;
            this.type = type;
            this.subject = subject;
            this.body = body;
            return result;
        }
    }
}
