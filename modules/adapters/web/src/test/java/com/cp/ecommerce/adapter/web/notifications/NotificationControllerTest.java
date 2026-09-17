package com.cp.ecommerce.adapter.web.notifications;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.web.notifications.mapper.NotificationWebMapper;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.port.incoming.GetNotificationInPort;
import com.cp.ecommerce.domain.notification.port.incoming.ListNotificationsInPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking notification controller's behavior and API responses.
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final String NOTIFICATIONS_ENDPOINT = "/api/notifications";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient ListNotificationsInPort listNotificationsInPort;

    @MockitoBean
    private transient GetNotificationInPort getNotificationInPort;

    @MockitoBean
    private transient NotificationWebMapper notificationWebMapper;

    @Test
    void shouldListNotifications() throws Exception {

        final Notification notification = NotificationBuilder.mockNotification();
        given(listNotificationsInPort.listNotifications()).willReturn(List.of(notification));
        given(notificationWebMapper.mapToResource(notification)).willReturn(Optional.of(mockNotificationResource()));

        mockMvc.perform(get(NOTIFICATIONS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$._embedded.notificationResourceList[0].notificationId")
                                .value(NotificationBuilder.TEST_NOTIFICATION_ID))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/hal+json")));
    }

    @Test
    void shouldListNotificationsForRecipient() throws Exception {

        final Notification notification = NotificationBuilder.mockNotification();
        given(listNotificationsInPort.listNotificationsForRecipient(NotificationBuilder.TEST_RECIPIENT_EMAIL))
                .willReturn(List.of(notification));
        given(notificationWebMapper.mapToResource(notification)).willReturn(Optional.of(mockNotificationResource()));

        mockMvc.perform(get(NOTIFICATIONS_ENDPOINT + "/recipient/" + NotificationBuilder.TEST_RECIPIENT_EMAIL))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$._embedded.notificationResourceList[0].recipientEmail")
                                .value(NotificationBuilder.TEST_RECIPIENT_EMAIL));
    }

    @Test
    void shouldListNotificationsByStatus() throws Exception {

        final Notification notification = NotificationBuilder.mockNotification();
        given(listNotificationsInPort.listNotificationsByStatus(NotificationStatus.SENT)).willReturn(List.of(notification));
        given(notificationWebMapper.mapToResource(notification)).willReturn(Optional.of(mockNotificationResource()));

        mockMvc.perform(get(NOTIFICATIONS_ENDPOINT + "/status/SENT"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._embedded.notificationResourceList[0]._links.self.href",
                                endsWith(NOTIFICATIONS_ENDPOINT + "/" + NotificationBuilder.TEST_NOTIFICATION_ID)));
    }

    @Test
    void shouldGetNotification() throws Exception {

        final Notification notification = NotificationBuilder.mockNotification();
        given(getNotificationInPort.getNotification(NotificationBuilder.TEST_NOTIFICATION_ID)).willReturn(notification);
        given(notificationWebMapper.mapToResource(notification)).willReturn(Optional.of(mockNotificationResource()));

        mockMvc.perform(get(NOTIFICATIONS_ENDPOINT + "/" + NotificationBuilder.TEST_NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(NotificationBuilder.TEST_NOTIFICATION_ID));
    }

    @Test
    void shouldReturnNotFoundWhenNotificationDoesNotExist() throws Exception {

        given(getNotificationInPort.getNotification(NotificationBuilder.TEST_NOTIFICATION_ID)).willReturn(null);

        mockMvc.perform(get(NOTIFICATIONS_ENDPOINT + "/" + NotificationBuilder.TEST_NOTIFICATION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMappingNotificationResourceReturnsEmpty() throws Exception {

        final Notification notification = NotificationBuilder.mockNotification();
        given(getNotificationInPort.getNotification(NotificationBuilder.TEST_NOTIFICATION_ID)).willReturn(notification);
        given(notificationWebMapper.mapToResource(notification)).willReturn(Optional.empty());

        mockMvc.perform(get(NOTIFICATIONS_ENDPOINT + "/" + NotificationBuilder.TEST_NOTIFICATION_ID))
                .andExpect(status().isInternalServerError());
    }

    private static NotificationResource mockNotificationResource() {

        return NotificationResource.builder()
                .notificationId(NotificationBuilder.TEST_NOTIFICATION_ID)
                .recipientEmail(NotificationBuilder.TEST_RECIPIENT_EMAIL)
                .channel(NotificationBuilder.TEST_CHANNEL.name())
                .type(NotificationBuilder.TEST_TYPE.name())
                .subject(NotificationBuilder.TEST_SUBJECT)
                .body(NotificationBuilder.TEST_BODY)
                .status(NotificationBuilder.TEST_STATUS.name())
                .createdDate(NotificationBuilder.TEST_CREATED_DATE)
                .sentDate(NotificationBuilder.TEST_SENT_DATE)
                .build();
    }

}
