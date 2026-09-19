package com.cp.ecommerce.adapter.persistence.notification;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.PageQuery;
import com.cp.ecommerce.domain.notification.PagedResult;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindNotificationsOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindNotificationsAdapter implements FindNotificationsOutPort {

    private final NotificationEntityRepository notificationEntityRepository;

    private final NotificationPersistenceMapper notificationPersistenceMapper;

    @Override
    public List<Notification> findAll() {

        return notificationEntityRepository.findAllByOrderByCreatedDateDesc()
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    @Override
    public List<Notification> findByRecipientEmail(final String recipientEmail) {

        return notificationEntityRepository.findByRecipientEmailOrderByCreatedDateDesc(recipientEmail)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    @Override
    public List<Notification> findByStatus(final NotificationStatus status) {

        return notificationEntityRepository.findByStatusOrderByCreatedDateDesc(status)
                .stream()
                .map(this::mapToDomainObjectOrThrow)
                .toList();
    }

    private Notification mapToDomainObjectOrThrow(final NotificationEntity entity) {

        return notificationPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification entity to domain object for notification id: "
                                        + entity.getNotificationId()));
    }

    @Override
    public PagedResult<Notification> findAll(final PageQuery q) {
        return page(notificationEntityRepository.findAllByOrderByCreatedDateDesc(PageRequest.of(q.page(), q.size())));
    }

    @Override
    public PagedResult<Notification> findByRecipientEmail(final String email, final PageQuery q) {
        return page(
                notificationEntityRepository
                        .findByRecipientEmailOrderByCreatedDateDesc(email, PageRequest.of(q.page(), q.size())));
    }

    @Override
    public PagedResult<Notification> findByStatus(final NotificationStatus status, final PageQuery q) {
        return page(
                notificationEntityRepository.findByStatusOrderByCreatedDateDesc(status, PageRequest.of(q.page(), q.size())));
    }

    private PagedResult<Notification> page(final Page<NotificationEntity> page) {
        return new PagedResult<>(
                page.getContent().stream().map(this::mapToDomainObjectOrThrow).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

}
