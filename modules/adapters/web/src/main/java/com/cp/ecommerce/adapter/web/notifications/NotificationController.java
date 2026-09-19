package com.cp.ecommerce.adapter.web.notifications;

import java.util.Optional;
import java.util.function.IntFunction;

import com.cp.ecommerce.adapter.web.notifications.mapper.NotificationWebMapper;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.PageQuery;
import com.cp.ecommerce.domain.notification.PagedResult;
import com.cp.ecommerce.domain.notification.port.incoming.GetNotificationInPort;
import com.cp.ecommerce.domain.notification.port.incoming.ListNotificationsInPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class NotificationController {

    private static final String NOTIFICATION_NOT_FOUND_MESSAGE = "Notification not found";

    private final ListNotificationsInPort listNotificationsInPort;

    private final GetNotificationInPort getNotificationInPort;

    private final NotificationWebMapper notificationWebMapper;

    @GetMapping
    @Operation(summary = "List notifications", description = "Newest first, page by page.")
    public PagedModel<EntityModel<NotificationResource>> listNotifications(
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<Notification> result = listNotificationsInPort.listNotifications(new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                target -> linkTo(methodOn(NotificationController.class).listNotifications(target, size)).withSelfRel());
    }

    @GetMapping("/recipient/{recipientEmail}")
    @Operation(summary = "List notifications for a recipient", description = "Newest first, page by page.")
    public PagedModel<EntityModel<NotificationResource>> listNotificationsForRecipient(
            @PathVariable("recipientEmail") final String recipientEmail,
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<Notification> result = listNotificationsInPort
                .listNotificationsForRecipient(recipientEmail, new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                target -> linkTo(
                        methodOn(NotificationController.class).listNotificationsForRecipient(recipientEmail, target, size))
                        .withSelfRel());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List notifications by status", description = "Newest first, page by page.")
    public PagedModel<EntityModel<NotificationResource>> listNotificationsByStatus(
            @PathVariable("status") final NotificationStatus status,
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<Notification> result = listNotificationsInPort
                .listNotificationsByStatus(status, new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                target -> linkTo(methodOn(NotificationController.class).listNotificationsByStatus(status, target, size))
                        .withSelfRel());
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

    private static void validatePage(final int page, final int size) {
        if (page < 0 || size < 1 || size > PageQuery.MAX_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and " + PageQuery.MAX_SIZE);
        }
    }

    private PagedModel<EntityModel<NotificationResource>> toPagedModel(
            final PagedResult<Notification> result,
            final int page,
            final IntFunction<Link> linkForPage) {
        final var content = result.content().stream().map(this::toResourceModel).toList();
        final var metadata = new PagedModel.PageMetadata(
                result.size(),
                result.page(),
                result.totalElements(),
                result.totalPages());
        final var model = PagedModel.of(content, metadata, linkForPage.apply(page).withSelfRel());
        final int lastPage = Math.max(result.totalPages() - 1, 0);
        model.add(linkForPage.apply(0).withRel(IanaLinkRelations.FIRST));
        if (page > 0) {

            model.add(linkForPage.apply(page - 1).withRel(IanaLinkRelations.PREV));
        }
        if (page < lastPage) {

            model.add(linkForPage.apply(page + 1).withRel(IanaLinkRelations.NEXT));
        }
        model.add(linkForPage.apply(lastPage).withRel(IanaLinkRelations.LAST));
        return model;
    }

}
