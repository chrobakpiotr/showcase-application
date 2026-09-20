package com.cp.ecommerce.domain.notification;

/** Ownership token for one notification delivery attempt. */
public record NotificationDeliveryClaim(Notification notification, String claimId) {
}
