package com.cp.ecommerce.adapter.web.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.InsufficientStockException;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.security.authentication.CurrentOperatorProvider;
import com.cp.ecommerce.adapter.web.order.mapper.OrderWebMapper;
import com.cp.ecommerce.adapter.web.order.metrics.OrderMetrics;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderPlacementResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.PlaceOrderResult;
import com.cp.ecommerce.domain.order.usecase.ListOrdersUseCase;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;
import com.cp.ecommerce.domain.order.usecase.RequestOrderCancellationUseCase;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import lombok.extern.slf4j.Slf4j;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Controller serving the functionality of {@link Order} API.
 * <p>
 * {@code ORDER_READ}/{@code ORDER_WRITE} grant access to every order in the system rather than only the caller's own - this is
 * a back-office/operator authorization model, not per-customer ownership scoping (see ADR 0017). Accountability is instead
 * provided by logging the acting operator's identity on every mutating action.
 * <p>
 * {@link #placeOrder} reserves stock for every line item via {@link ManageStockInPort#reserveStock} before the order is ever
 * saved, composed here rather than in the domain layer so that {@code order.Order} carries no dependency on
 * {@code inventory.StockLevel} (see ADR 0029) - the same "cross-context composition happens in the web layer, not the domain"
 * stance already taken by {@code CartController} (ADR 0027). {@link #cancelOrder} releases that same reservation on a
 * customer-initiated cancellation; the order-placement saga's own internal compensation path releases it separately (see
 * {@code OrderPlacementSagaOrchestrator}), since that path never goes through this controller.
 * <p>
 * Payment capture happens asynchronously as a saga step, not synchronously in {@link #placeOrder} (see ADR 0030) - unlike
 * stock, which must be reserved up front to prevent overselling, whether a charge succeeds has no bearing on whether the order
 * can be durably recorded. {@link #cancelOrder} refunds via {@link ManagePaymentInPort#refundPayment}, unconditionally and
 * symmetric with {@code releaseStockFor}, since that method is itself an idempotent no-op if payment was never captured.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/order")
@Tag(name = "Order", description = "Placing and retrieving orders")
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class OrderController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final String PLACE_ORDER_RATE_LIMITER = "placeOrder";

    private static final String CANCEL_ORDER_RATE_LIMITER = "cancelOrder";

    private final PlaceOrderUseCase placeOrderUseCase;

    private final ManageOrderUseCase manageOrderUseCase;

    private final RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    private final ListOrdersUseCase listOrdersUseCase;

    private final OrderWebMapper orderWebMapper;

    private final OrderMetrics orderMetrics;

    private final RateLimitedExecutor rateLimitedExecutor;

    private final CurrentOperatorProvider currentOperatorProvider;

    private final ManageStockInPort manageStockInPort;

    private final ManagePaymentInPort managePaymentInPort;

    private final GetPaymentInPort getPaymentInPort;

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
            content = @Content(schema = @Schema(implementation = OrderPlacementResource.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Idempotency-Key was already used for a different request, or is still being processed, or "
                    + "insufficient stock is available for one of the order's line items",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many order placement requests; retry after a short delay",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Order data is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public OrderPlacementResource placeOrder(
            @RequestBody final OrderResource orderResource,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) final String idempotencyKey) {

        final Order order = orderWebMapper.mapToDomainObject(orderResource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order data is missing"));
        order.assertValidationsEmpty();
        reserveStockFor(order);
        final PlaceOrderResult result = rateLimitedExecutor
                .callRateLimited(PLACE_ORDER_RATE_LIMITER, () -> placeOrderUseCase.placeOrder(order, idempotencyKey));
        if (result.newlyPlaced()) {

            orderMetrics.recordOrderPlaced();
            log.info(
                    "Order {} placed by operator {}",
                    result.orderNumber(),
                    currentOperatorProvider.currentOperator().orElse("unknown"));
        }
        return new OrderPlacementResource(result.orderNumber());
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Returns a page of orders ordered by creation date, most recent first.")
    @ApiResponse(
            responseCode = "200",
            description = "Page of orders",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = OrderDetailsResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "page is negative, or size is not between 1 and " + PageQuery.MAX_SIZE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public PagedModel<EntityModel<OrderDetailsResource>> listOrders(
            @Parameter(description = "Zero-based page index") @RequestParam(name = "page", defaultValue = "0") final int page,
            @Parameter(description = "Page size") @RequestParam(
                    name = "size",
                    defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {

        if (page < 0 || size < 1 || size > PageQuery.MAX_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and " + PageQuery.MAX_SIZE);
        }
        final PagedResult<Order> result = listOrdersUseCase.listOrders(new PageQuery(page, size));
        final List<EntityModel<OrderDetailsResource>> content = result.content()
                .stream()
                .map(order -> toResourceWithLinks(order, order.getOrderNumber()))
                .toList();
        final PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                result.size(),
                result.page(),
                result.totalElements(),
                result.totalPages());
        final PagedModel<EntityModel<OrderDetailsResource>> pagedModel = PagedModel
                .of(content, metadata, linkTo(methodOn(OrderController.class).listOrders(page, size)).withSelfRel());
        final int lastPage = Math.max(result.totalPages() - 1, 0);
        pagedModel.add(linkTo(methodOn(OrderController.class).listOrders(0, size)).withRel(IanaLinkRelations.FIRST));
        if (page > 0) {

            pagedModel.add(linkTo(methodOn(OrderController.class).listOrders(page - 1, size)).withRel(IanaLinkRelations.PREV));
        }
        if (page < lastPage) {

            pagedModel.add(linkTo(methodOn(OrderController.class).listOrders(page + 1, size)).withRel(IanaLinkRelations.NEXT));
        }
        pagedModel.add(linkTo(methodOn(OrderController.class).listOrders(lastPage, size)).withRel(IanaLinkRelations.LAST));
        return pagedModel;
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Find an order by its number")
    @ApiResponse(
            responseCode = "200",
            description = "Order found",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = OrderDetailsResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Order not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<OrderDetailsResource> findOrder(@PathVariable("orderNumber") final String orderNumber) {

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (Optional.ofNullable(order).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return toResourceWithLinks(order, orderNumber);
    }

    @PostMapping("/{orderNumber}/cancel")
    @Operation(
            summary = "Cancel an order",
            description = "Cancels an order on the customer's behalf. Only an order still in CONFIRMED status can be "
                    + "cancelled this way; this is the same underlying mechanism the order-placement saga itself uses to "
                    + "compensate a failed order, exposed here under an explicit state-machine guard instead of being "
                    + "unconditional.")
    @ApiResponse(
            responseCode = "200",
            description = "Order cancelled",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = OrderDetailsResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Order not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Order is no longer in a cancellable state (e.g. already cancelled)",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "429",
            description = "Too many order cancellation requests; retry after a short delay",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<OrderDetailsResource> cancelOrder(@PathVariable("orderNumber") final String orderNumber) {

        final Order order = rateLimitedExecutor.callRateLimited(
                CANCEL_ORDER_RATE_LIMITER,
                () -> requestOrderCancellationUseCase.requestCancellation(orderNumber));
        if (Optional.ofNullable(order).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        releaseStockFor(order);
        refundPaymentFor(order);
        orderMetrics.recordOrderCancelled();
        log.info("Order {} cancelled by operator {}", orderNumber, currentOperatorProvider.currentOperator().orElse("unknown"));
        return toResourceWithLinks(order, orderNumber);
    }

    // Reserves stock for every line item before the order is ever saved, so an order can never be placed for a SKU
    // that does not actually have enough available stock. If a later item in the list cannot be reserved, every
    // earlier reservation made by this same call is released first, so a rejected order placement never leaves a
    // partial reservation behind.
    private void reserveStockFor(final Order order) {

        final List<OrderLineItem> reserved = new ArrayList<>();
        try {
            for (final OrderLineItem item : order.getItems()) {

                manageStockInPort.reserveStock(item.getSku(), item.getQuantity());
                reserved.add(item);
            }
        } catch (final InsufficientStockException exception) {

            reserved.forEach(item -> manageStockInPort.releaseStock(item.getSku(), item.getQuantity()));
            throw exception;
        }
    }

    // Releases a cancelled order's stock reservation. Safe to call unconditionally: ManageStockInPort#releaseStock
    // clamps reserved quantity at zero rather than going negative, so this is a no-op for any SKU that was never
    // actually reserved (e.g. a legacy order placed before this reservation step existed).
    private void releaseStockFor(final Order order) {

        order.getItems().forEach(item -> manageStockInPort.releaseStock(item.getSku(), item.getQuantity()));
    }

    // Refunds a cancelled order's payment. Safe to call unconditionally: ManagePaymentInPort#refundPayment is an
    // idempotent no-op if the saga hasn't captured payment for this order yet (e.g. cancelling before the payment-
    // capture saga step has even run), matching releaseStockFor's convention above (see ADR 0030).
    private void refundPaymentFor(final Order order) {

        managePaymentInPort.refundPayment(order.getOrderNumber());
    }

    // Affordance-driven HATEOAS: the "cancel" link is only advertised while the order is actually cancellable, so a client
    // can rely on the link's mere presence rather than duplicating the CONFIRMED-only business rule enforced server-side by
    // RequestOrderCancellationUseCase. Also composes in the order's payment transaction (a separate bounded context, see
    // ADR 0030) so a client can see its status without a second round-trip.
    private EntityModel<OrderDetailsResource> toResourceWithLinks(final Order order, final String orderNumber) {

        final OrderDetailsResource resource = orderWebMapper.mapToResource(order, getPaymentInPort.getPayment(orderNumber))
                .orElseThrow(() -> new TechnicalProblemException("Order data is missing"));
        final EntityModel<OrderDetailsResource> model = EntityModel
                .of(resource, linkTo(methodOn(OrderController.class).findOrder(orderNumber)).withSelfRel());
        if (order.getStatus() == OrderStatus.CONFIRMED) {

            model.add(linkTo(methodOn(OrderController.class).cancelOrder(orderNumber)).withRel("cancel"));
        }
        return model;
    }

}
