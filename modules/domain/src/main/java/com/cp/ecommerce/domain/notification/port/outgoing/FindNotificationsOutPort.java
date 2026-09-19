package com.cp.ecommerce.domain.notification.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.PageQuery;
import com.cp.ecommerce.domain.notification.PagedResult;

/**
 * Outgoing persistence port for querying notification log entries.
 */
public interface FindNotificationsOutPort {

    List<Notification> findAll();

    List<Notification> findByRecipientEmail(String recipientEmail);

    List<Notification> findByStatus(NotificationStatus status);

    PagedResult<Notification> findAll(PageQuery pageQuery);

    PagedResult<Notification> findByRecipientEmail(String recipientEmail, PageQuery pageQuery);

    PagedResult<Notification> findByStatus(NotificationStatus status, PageQuery pageQuery);

}
