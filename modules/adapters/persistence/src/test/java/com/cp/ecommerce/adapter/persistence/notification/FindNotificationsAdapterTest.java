package com.cp.ecommerce.adapter.persistence.notification;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.NotificationEntityBuilder;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.PageQuery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindNotificationsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindNotificationsAdapterTest {

    @InjectMocks
    private transient FindNotificationsAdapter findNotificationsAdapter;

    @Mock
    private transient NotificationEntityRepository notificationEntityRepository;

    @Mock
    private transient NotificationPersistenceMapper notificationPersistenceMapper;

    @Test
    void shouldFindAllNotifications() {

        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(List.of(entity)).when(notificationEntityRepository).findAllByOrderByCreatedDateDesc();
        doReturn(Optional.of(NotificationBuilder.mockNotification())).when(notificationPersistenceMapper)
                .mapToDomainObject(eq(entity));

        assertThat(findNotificationsAdapter.findAll()).hasSize(1);
    }

    @Test
    void shouldFindNotificationsByRecipientEmail() {

        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(List.of(entity)).when(notificationEntityRepository)
                .findByRecipientEmailOrderByCreatedDateDesc(NotificationBuilder.TEST_RECIPIENT_EMAIL);
        doReturn(Optional.of(NotificationBuilder.mockNotification())).when(notificationPersistenceMapper)
                .mapToDomainObject(eq(entity));

        assertThat(findNotificationsAdapter.findByRecipientEmail(NotificationBuilder.TEST_RECIPIENT_EMAIL)).hasSize(1);
    }

    @Test
    void shouldFindNotificationsByStatus() {

        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(List.of(entity)).when(notificationEntityRepository)
                .findByStatusOrderByCreatedDateDesc(NotificationStatus.SENT);
        doReturn(Optional.of(NotificationBuilder.mockNotification())).when(notificationPersistenceMapper)
                .mapToDomainObject(eq(entity));

        assertThat(findNotificationsAdapter.findByStatus(NotificationStatus.SENT)).hasSize(1);
    }

    @Test
    void shouldThrowWhenFindAllCannotMapNotification() {

        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(List.of(entity)).when(notificationEntityRepository).findAllByOrderByCreatedDateDesc();
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findNotificationsAdapter.findAll()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NotificationBuilder.TEST_NOTIFICATION_ID);
    }

    @Test
    void shouldFindPagedNotifications() {
        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(new PageImpl<>(List.of(entity), PageRequest.of(1, 10), 25)).when(notificationEntityRepository)
                .findAllByOrderByCreatedDateDesc(PageRequest.of(1, 10));
        doReturn(Optional.of(NotificationBuilder.mockNotification())).when(notificationPersistenceMapper)
                .mapToDomainObject(eq(entity));
        final var result = findNotificationsAdapter.findAll(new PageQuery(1, 10));
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldFindPagedNotificationsByRecipientEmail() {
        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 5), 1)).when(notificationEntityRepository)
                .findByRecipientEmailOrderByCreatedDateDesc(NotificationBuilder.TEST_RECIPIENT_EMAIL, PageRequest.of(0, 5));
        doReturn(Optional.of(NotificationBuilder.mockNotification())).when(notificationPersistenceMapper)
                .mapToDomainObject(eq(entity));
        assertThat(
                findNotificationsAdapter.findByRecipientEmail(NotificationBuilder.TEST_RECIPIENT_EMAIL, new PageQuery(0, 5))
                        .content())
                .hasSize(1);
    }

    @Test
    void shouldFindPagedNotificationsByStatus() {
        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 5), 1)).when(notificationEntityRepository)
                .findByStatusOrderByCreatedDateDesc(NotificationStatus.SENT, PageRequest.of(0, 5));
        doReturn(Optional.of(NotificationBuilder.mockNotification())).when(notificationPersistenceMapper)
                .mapToDomainObject(eq(entity));
        assertThat(findNotificationsAdapter.findByStatus(NotificationStatus.SENT, new PageQuery(0, 5)).content()).hasSize(1);
    }

}
