package com.cp.ecommerce.adapter.web.notifications.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.domain.notification.Notification;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping {@link Notification} objects to web resources.
 */
@Component
public class NotificationWebMapper implements WebResponseMapper<Notification, NotificationResource> {

    @Override
    public Optional<NotificationResource> mapToResource(final Notification notification) {

        return Optional.ofNullable(notification)
                .map(
                        domain -> NotificationResource.builder()
                                .notificationId(domain.getNotificationId())
                                .recipientEmail(domain.getRecipientEmail())
                                .channel(domain.getChannel().name())
                                .type(domain.getType().name())
                                .subject(domain.getSubject())
                                .body(domain.getBody())
                                .status(domain.getStatus().name())
                                .createdDate(domain.getCreatedDate())
                                .sentDate(domain.getSentDate())
                                .build());
    }

}
