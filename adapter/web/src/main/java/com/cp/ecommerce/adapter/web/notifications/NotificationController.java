package com.cp.ecommerce.adapter.web.notifications;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.notifications.mapper.NotificationWebMapper;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.port.incoming.GetNotificationInPort;
import com.cp.ecommerce.domain.notification.port.incoming.ListNotificationsInPort;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Controller serving the back-office notification log API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Reading persisted notification log entries")
public class NotificationController {

    private static final String NOTIFICATION_NOT_FOUND_MESSAGE = "Notification not found";

    private final ListNotificationsInPort listNotificationsInPort;

    private final GetNotificationInPort getNotificationInPort;

    private final NotificationWebMapper notificationWebMapper;

    @GetMapping
    @Operation(summary = "List notifications", description = "Newest first. Empty list if there are none.")
    public CollectionModel<EntityModel<NotificationResource>> listNotifications() {

        return CollectionModel.of(
                listNotificationsInPort.listNotifications().stream().map(this::toResourceModel).toList(),
                linkTo(methodOn(NotificationController.class).listNotifications()).withSelfRel());
    }

    @GetMapping("/recipient/{recipientEmail}")
    @Operation(summary = "List notifications for a recipient", description = "Newest first. Empty list if there are none.")
    public CollectionModel<EntityModel<NotificationResource>> listNotificationsForRecipient(
            @PathVariable("recipientEmail") final String recipientEmail) {

        return CollectionModel.of(
                listNotificationsInPort.listNotificationsForRecipient(recipientEmail)
                        .stream()
                        .map(this::toResourceModel)
                        .toList(),
                linkTo(methodOn(NotificationController.class).listNotificationsForRecipient(recipientEmail)).withSelfRel());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List notifications by status", description = "Newest first. Empty list if there are none.")
    public CollectionModel<EntityModel<NotificationResource>> listNotificationsByStatus(
            @PathVariable("status") final NotificationStatus status) {

        return CollectionModel.of(
                listNotificationsInPort.listNotificationsByStatus(status).stream().map(this::toResourceModel).toList(),
                linkTo(methodOn(NotificationController.class).listNotificationsByStatus(status)).withSelfRel());
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "Find a notification by its id")
    @ApiResponse(
            responseCode = "404",
            description = NOTIFICATION_NOT_FOUND_MESSAGE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<NotificationResource> getNotification(@PathVariable("notificationId") final String notificationId) {

        final Notification notification = getNotificationInPort.getNotification(notificationId);
        if (Optional.ofNullable(notification).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOTIFICATION_NOT_FOUND_MESSAGE);
        }
        return toResourceModel(notification);
    }

    private EntityModel<NotificationResource> toResourceModel(final Notification notification) {

        return EntityModel.of(
                notificationWebMapper.mapToResource(notification)
                        .orElseThrow(() -> new TechnicalProblemException("Notification data is missing")),
                linkTo(methodOn(NotificationController.class).getNotification(notification.getNotificationId())).withSelfRel());
    }

}
