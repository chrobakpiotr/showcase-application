package com.cp.ecommerce.adapter.web.order;

import java.util.List;

import com.cp.ecommerce.adapter.web.order.resource.OrderRecoveryTimelineEntryResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderRecoveryTimelineResource;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;
import com.cp.ecommerce.domain.order.usecase.FindOrderRecoveryTimelineUseCase;

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

/**
 * Read-only order recovery projection. This is current durable workflow evidence, not an event-sourcing API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
@Tag(name = "Order recovery", description = "Read-only durable recovery projection for an order")
public class OrderRecoveryTimelineController {

    static final int DEFAULT_SIZE = 50;

    static final int MAX_SIZE = 100;

    static final int MAX_PAGE = 10_000;

    private final FindOrderRecoveryTimelineUseCase findOrderRecoveryTimelineUseCase;

    @GetMapping("/{orderNumber}/recovery-timeline")
    @Operation(
            summary = "Read order recovery timeline",
            description = "Projects existing durable recovery records. It is not an event store and exposes no redrive action.")
    @ApiResponse(
            responseCode = "200",
            description = "Bounded recovery projection",
            content = @Content(schema = @Schema(implementation = OrderRecoveryTimelineResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "page/size is outside the bounded query window",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public OrderRecoveryTimelineResource find(
            @PathVariable("orderNumber") final String orderNumber,
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_SIZE) final int size) {

        validateWindow(page, size);
        final List<OrderRecoveryTimelineEntryResource> items = findOrderRecoveryTimelineUseCase.find(orderNumber, page, size)
                .stream()
                .map(OrderRecoveryTimelineController::toResource)
                .toList();

        return OrderRecoveryTimelineResource.builder().orderNumber(orderNumber).page(page).size(size).items(items).build();
    }

    private static void validateWindow(final int page, final int size) {

        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be between 0 and " + MAX_PAGE + " and size must be between 1 and " + MAX_SIZE);
        }
    }

    private static OrderRecoveryTimelineEntryResource toResource(final OrderRecoveryTimelineEntry entry) {

        return OrderRecoveryTimelineEntryResource.builder()
                .source(entry.source())
                .type(entry.type())
                .state(entry.state())
                .occurredAt(entry.occurredAt())
                .referenceId(entry.referenceId())
                .summary(entry.summary())
                .build();
    }
}
