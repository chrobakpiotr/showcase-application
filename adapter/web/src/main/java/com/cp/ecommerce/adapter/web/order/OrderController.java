package com.cp.ecommerce.adapter.web.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.order.mapper.OrderWebMapper;
import com.cp.ecommerce.adapter.web.order.metrics.OrderMetrics;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
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

    private final PlaceOrderUseCase placeOrderUseCase;

    private final ManageOrderUseCase manageOrderUseCase;

    private final OrderWebMapper orderWebMapper;

    private final OrderMetrics orderMetrics;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Place a new order",
            description = "Validates and persists a new order, returning its generated order number.")
    @ApiResponse(
            responseCode = "201",
            description = "Order successfully placed",
            content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Order data is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public String placeOrder(@RequestBody final OrderResource orderResource) {

        final Order order = orderWebMapper.mapToDomainObject(orderResource).orElse(null);
        Optional.ofNullable(order).ifPresentOrElse(Order::assertValidationsEmpty, () -> {
            throw new TechnicalProblemException("Order data is missing");
        });
        final String orderNumber = placeOrderUseCase.placeOrder(order);
        orderMetrics.recordOrderPlaced();
        return orderNumber;
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
