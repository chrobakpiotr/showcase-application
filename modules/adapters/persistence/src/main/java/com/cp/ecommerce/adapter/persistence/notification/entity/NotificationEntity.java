package com.cp.ecommerce.adapter.persistence.notification.entity;

import java.util.Date;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Notification} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "NOTIFICATION")
public class NotificationEntity {

    @Id
    @Column(name = "NOTIFICATION_ID", length = 42, nullable = false)
    private String notificationId;

    @Column(name = "RECIPIENT_EMAIL", length = 255, nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "CHANNEL", length = 20, nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 30, nullable = false)
    private NotificationType type;

    @Column(name = "SUBJECT", length = 255, nullable = false)
    private String subject;

    @Column(name = "BODY", length = 2000, nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private NotificationStatus status;

    @Column(name = "CREATED_DATE", nullable = false)
    private Date createdDate;

    @Column(name = "SENT_DATE")
    private Date sentDate;

}
