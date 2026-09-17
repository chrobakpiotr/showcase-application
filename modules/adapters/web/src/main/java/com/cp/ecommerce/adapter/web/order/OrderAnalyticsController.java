package com.cp.ecommerce.adapter.web.order;

import java.util.List;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.order.mapper.OrderAnalyticsWebMapper;
import com.cp.ecommerce.adapter.web.order.resource.OrderAnalyticsResource;
import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.usecase.FindRecentOrderAnalyticsUseCase;

import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Controller serving the order-analytics read model, populated by {@code OrderAnalyticsEventConsumer} (adapter:kafka) consuming
 * the Kafka order-analytics event stream.
 *
 * <p>
 * Deliberately kept separate from {@link OrderController}: it serves a read-only projection derived from an asynchronous event
 * stream, not the transactional order aggregate itself. Mapped under {@code /api/order/**} so it is covered by the existing
 * {@code ORDER_READ} security matcher in {@code WebSecurityConfiguration} with zero security-config changes.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/order/analytics")
@Tag(name = "Order analytics", description = "Read-only order-analytics projection, populated by the Kafka consumer")
public class OrderAnalyticsController {

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private final FindRecentOrderAnalyticsUseCase findRecentOrderAnalyticsUseCase;

    private final OrderAnalyticsWebMapper orderAnalyticsWebMapper;

    @GetMapping("/recent")
    @Operation(
            summary = "List recent order-analytics events",
            description = "Returns the most recently consumed order-analytics events, most recently consumed first. This "
                    + "is a best-effort read model: if Kafka is disabled, or the consumer has not processed anything yet, "
                    + "the list is simply empty rather than an error.")
    @ApiResponse(
            responseCode = "200",
            description = "Recent order-analytics events",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = OrderAnalyticsResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "limit is not between 1 and " + MAX_LIMIT,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CollectionModel<OrderAnalyticsResource> findRecent(
            @Parameter(description = "Maximum number of events to return") @RequestParam(
                    name = "limit",
                    defaultValue = "" + DEFAULT_LIMIT) final int limit) {

        if (limit < 1 || limit > MAX_LIMIT) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
        final List<OrderAnalyticsResource> content = findRecentOrderAnalyticsUseCase.findRecent(limit)
                .stream()
                .map(this::toResourceOrThrow)
                .toList();
        return CollectionModel.of(content, linkTo(methodOn(OrderAnalyticsController.class).findRecent(limit)).withSelfRel());
    }

    private OrderAnalyticsResource toResourceOrThrow(final OrderAnalyticsProjection projection) {

        return orderAnalyticsWebMapper.mapToResource(projection)
                .orElseThrow(() -> new TechnicalProblemException("Order analytics data is missing"));
    }

}
