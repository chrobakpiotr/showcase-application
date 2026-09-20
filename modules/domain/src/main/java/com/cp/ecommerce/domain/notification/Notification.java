package com.cp.ecommerce.domain.notification;

import java.time.Instant;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A persisted, queryable notification log entry created by other bounded contexts.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Notification extends ValidDomainObject<Notification> {

    @Size(max = ValidationConstants.NOTIFICATION_ID_MAX, message = ValidationConstants.INVALID_NOTIFICATION_ID)
    String notificationId;

    String eventKey;

    @NotBlank(message = ValidationConstants.INVALID_NOTIFICATION_RECIPIENT_EMAIL)
    @Email(message = ValidationConstants.INVALID_NOTIFICATION_RECIPIENT_EMAIL)
    @Size(max = ValidationConstants.CONTACT_EMAIL_MAX, message = ValidationConstants.INVALID_NOTIFICATION_RECIPIENT_EMAIL)
    String recipientEmail;

    @NotNull(message = ValidationConstants.INVALID_NOTIFICATION_CHANNEL)
    @Builder.Default
    NotificationChannel channel = NotificationChannel.EMAIL;

    @NotNull(message = ValidationConstants.INVALID_NOTIFICATION_TYPE)
    NotificationType type;

    @NotBlank(message = ValidationConstants.INVALID_NOTIFICATION_SUBJECT)
    @Size(max = ValidationConstants.NOTIFICATION_SUBJECT_MAX, message = ValidationConstants.INVALID_NOTIFICATION_SUBJECT)
    String subject;

    @NotBlank(message = ValidationConstants.INVALID_NOTIFICATION_BODY)
    @Size(max = ValidationConstants.NOTIFICATION_BODY_MAX, message = ValidationConstants.INVALID_NOTIFICATION_BODY)
    String body;

    @NotNull(message = ValidationConstants.INVALID_NOTIFICATION_STATUS)
    @Builder.Default
    NotificationStatus status = NotificationStatus.PENDING;

    @NotNull(message = ValidationConstants.INVALID_NOTIFICATION_CREATED_DATE)
    Instant createdDate;

    Instant sentDate;

    public static NotificationBuilder builder() {

        return new NotificationBuilder() {

            @Override
            public Notification build() {

                return super.build().validate();
            }
        };
    }

}
