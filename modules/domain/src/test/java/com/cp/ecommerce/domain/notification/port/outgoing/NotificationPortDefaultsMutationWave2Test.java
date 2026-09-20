package com.cp.ecommerce.domain.notification.port.outgoing;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationDeliveryClaim;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPortDefaultsMutationWave2Test {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void deliverCompatibilityHelperShouldForwardDurableIdAndSameNotification() {
        final RecordingDeliveryPort port = new RecordingDeliveryPort();
        final Notification notification = notification(NotificationStatus.PENDING);

        port.deliver(notification);

        assertThat(port.operationId).isEqualTo(notification.getNotificationId());
        assertThat(port.notification).isSameAs(notification);
    }

    @Test
    void claimCompatibilityHelperShouldPreserveNullAndClaimedNotification() {
        final RecordingManagementPort port = new RecordingManagementPort();
        final Notification notification = notification(NotificationStatus.DELIVERING);
        port.claim = new NotificationDeliveryClaim(notification, "claim-1");

        assertThat(port.claim("N-1", NOW)).isSameAs(notification);

        port.claim = null;
        assertThat(port.claim("N-1", NOW)).isNull();
    }

    @Test
    void completionCompatibilityHelpersShouldDelegateNullClaimAndReturnExactValue() {
        final RecordingManagementPort port = new RecordingManagementPort();
        final Notification sent = notification(NotificationStatus.SENT);
        final Notification failed = notification(NotificationStatus.FAILED);
        port.sent = sent;
        port.failed = failed;

        assertThat(port.markSent("N-1", NOW)).isSameAs(sent);
        assertThat(port.sentClaimId).isNull();

        assertThat(port.markFailed("N-1", "boom", NOW)).isSameAs(failed);
        assertThat(port.failedClaimId).isNull();
        assertThat(port.failedError).isEqualTo("boom");
    }

    private static Notification notification(final NotificationStatus status) {
        return Notification.builder()
                .notificationId("N-1")
                .eventKey("event")
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("subject")
                .body("body")
                .status(status)
                .createdDate(NOW)
                .build();
    }

    private static final class RecordingDeliveryPort implements DeliverNotificationOutPort {

        private String operationId;
        private Notification notification;

        @Override
        public void deliver(final String operationId, final Notification notification) {
            this.operationId = operationId;
            this.notification = notification;
        }
    }

    private static final class RecordingManagementPort implements ManageNotificationDeliveryOutPort {

        private NotificationDeliveryClaim claim;
        private Notification sent;
        private Notification failed;
        private String sentClaimId;
        private String failedClaimId;
        private String failedError;

        @Override
        public List<String> findDueNotificationIds(final Instant now, final int limit) {
            return List.of();
        }

        @Override
        public NotificationDeliveryClaim claimDelivery(final String notificationId, final Instant now) {
            return claim;
        }

        @Override
        public Notification markSent(final String notificationId, final String claimId, final Instant sentDate) {
            sentClaimId = claimId;
            return sent;
        }

        @Override
        public Notification markFailed(
                final String notificationId,
                final String claimId,
                final String error,
                final Instant failedAt) {
            failedClaimId = claimId;
            failedError = error;
            return failed;
        }
    }
}
