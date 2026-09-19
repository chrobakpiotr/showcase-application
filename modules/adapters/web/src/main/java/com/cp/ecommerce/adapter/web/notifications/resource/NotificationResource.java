package com.cp.ecommerce.adapter.web.notifications.resource;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Resource representing a notification log entry.
 */
@Builder
public record NotificationResource(@Schema(example = "NOTIF-3f2504e0-4f89-11d3-9a0c-0305e82c3301") String notificationId,
        @Schema(example = "customer@example.com") String recipientEmail, @Schema(example = "EMAIL") String channel,
        @Schema(example = "ORDER_CONFIRMED") String type, @Schema(example = "Order ORD-20240907-0001 confirmed") String subject,
        @Schema(example = "Your order ORD-20240907-0001 was confirmed.") String body, @Schema(example = "SENT") String status,
        Instant createdDate, Instant sentDate) {

}
