package com.cp.ecommerce.adapter.persistence.notification.entity;

import java.util.List;

import com.cp.ecommerce.domain.notification.NotificationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link NotificationEntity}.
 */
public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, String> {

    NotificationEntity findByNotificationId(String notificationId);

    List<NotificationEntity> findAllByOrderByCreatedDateDesc();

    List<NotificationEntity> findByRecipientEmailOrderByCreatedDateDesc(String recipientEmail);

    List<NotificationEntity> findByStatusOrderByCreatedDateDesc(NotificationStatus status);

}
