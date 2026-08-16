package com.cp.ecommerce.adapter.web.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.order.mapper.OrderWebMapper;
import com.cp.ecommerce.adapter.web.order.metrics.OrderMetrics;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controller serving the functionality of {@link Order} API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/order")
@Tag(name = "Order", description = "Placing and retrieving orders")
public class OrderController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PlaceOrderUseCase placeOrderUseCase;

    private final ManageOrderUseCase manageOrderUseCase;

    private final OrderWebMapper orderWebMapper;

    private final OrderMetrics orderMetrics;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Place a new order",
            description = "Validates and persists a new order, returning its generated order number. An optional "
                    + "Idempotency-Key header makes retries safe: repeating the same request with the same key replays "
                    + "the original result instead of placing a second order.")
    @Parameter(
            name = IDEMPOTENCY_KEY_HEADER,
            in = ParameterIn.HEADER,
            required = false,
            description = "Client-generated token identifying this exact order-placement attempt. Repeating the request "
                    + "with the same key is processed at most once.",
            schema = @Schema(type = "string"))
    @ApiResponse(
            responseCode = "201",
            description = "Order successfully placed",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Idempotency-Key was already used for a different request, or is still being processed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Order data is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public String placeOrder(
            @RequestBody final OrderResource orderResource,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) final String idempotencyKey) {

        final Order order = orderWebMapper.mapToDomainObject(orderResource).orElse(null);
        Optional.ofNullable(order).ifPresentOrElse(Order::assertValidationsEmpty, () -> {
            throw new TechnicalProblemException("Order data is missing");
        });
        final PlaceOrderResult result = placeOrderUseCase.placeOrder(order, idempotencyKey);
        if (result.newlyPlaced()) {

            orderMetrics.recordOrderPlaced();
        }
        return result.orderNumber();
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Find an order by its number")
    @ApiResponse(
            responseCode = "200",
            description = "Order found",
            content = @Content(schema = @Schema(implementation = Order.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Order not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public Order findOrder(@PathVariable("orderNumber") final String orderNumber) {

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (Optional.ofNullable(order).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order;
    }

}
