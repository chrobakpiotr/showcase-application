package com.cp.ecommerce.adapter.persistence.notification.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.notification.NotificationStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Spring Data repository for {@link NotificationEntity}.
 */
public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, String> {

    NotificationEntity findByNotificationId(String notificationId);

    List<NotificationEntity> findAllByOrderByCreatedDateDesc();

    Page<NotificationEntity> findAllByOrderByCreatedDateDesc(Pageable pageable);

    List<NotificationEntity> findByRecipientEmailOrderByCreatedDateDesc(String recipientEmail);

    Page<NotificationEntity> findByRecipientEmailOrderByCreatedDateDesc(String recipientEmail, Pageable pageable);

    List<NotificationEntity> findByStatusOrderByCreatedDateDesc(NotificationStatus status);

    Page<NotificationEntity> findByStatusOrderByCreatedDateDesc(NotificationStatus status, Pageable pageable);

    @Query("""
            select notification.notificationId
            from NotificationEntity notification
            where notification.status in :statuses
              and notification.nextAttemptDate <= :now
            order by notification.nextAttemptDate asc, notification.createdDate asc
            """)
    List<String> findDueNotificationIds(
            @Param("statuses") List<NotificationStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("""
            select min(notification.nextAttemptDate)
            from NotificationEntity notification
            where notification.status in :statuses
              and notification.nextAttemptDate <= :now
            """)
    Instant findOldestDueAttemptDate(@Param("statuses") List<NotificationStatus> statuses, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from NotificationEntity notification where notification.notificationId = :notificationId")
    Optional<NotificationEntity> findByNotificationIdForUpdate(@Param("notificationId") String notificationId);
}
