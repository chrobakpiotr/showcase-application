package com.cp.ecommerce.adapter.web.shipments;

import java.util.Optional;
import java.util.function.IntFunction;

import com.cp.ecommerce.adapter.web.shipments.mapper.ShipmentWebMapper;
import com.cp.ecommerce.adapter.web.shipments.resource.CreateShipmentResource;
import com.cp.ecommerce.adapter.web.shipments.resource.ShipmentResource;
import com.cp.ecommerce.application.shipment.ShipmentWorkflow;
import com.cp.ecommerce.domain.shipment.PageQuery;
import com.cp.ecommerce.domain.shipment.PagedResult;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.GetShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.ListShipmentsInPort;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * Controller serving the back-office shipping / fulfillment-tracking API.
 *
 * <p>
 * Cross-bounded-context composition stays here, not in {@code domain.shipment}: the controller validates the referenced
 * {@link Order}, and emits notification log entries after status changes by composing {@link SendNotificationInPort} in the web
 * layer.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/shipments")
@Tag(name = "Shipments", description = "Creating, reading and advancing shipping / fulfillment tracking")
public class ShipmentController {

    private static final String SHIPMENT_NOT_FOUND_MESSAGE = "Shipment not found";

    private final GetShipmentInPort getShipmentInPort;

    private final ShipmentWorkflow shipmentWorkflow;

    private final ListShipmentsInPort listShipmentsInPort;

    private final ShipmentWebMapper shipmentWebMapper;

    @GetMapping
    @Operation(summary = "List shipments", description = "Newest first, page by page.")
    public PagedModel<EntityModel<ShipmentResource>> listShipments(
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<Shipment> result = listShipmentsInPort.listShipments(new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                size,
                target -> linkTo(methodOn(ShipmentController.class).listShipments(target, size)).withSelfRel());
    }

    @GetMapping("/order/{orderNumber}")
    @Operation(summary = "List shipments for an order", description = "Newest first, page by page.")
    public PagedModel<EntityModel<ShipmentResource>> listShipmentsForOrder(
            @PathVariable("orderNumber") final String orderNumber,
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<Shipment> result = listShipmentsInPort.listShipmentsForOrder(orderNumber, new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                size,
                target -> linkTo(methodOn(ShipmentController.class).listShipmentsForOrder(orderNumber, target, size))
                        .withSelfRel());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List shipments by status", description = "Newest first, page by page.")
    public PagedModel<EntityModel<ShipmentResource>> listShipmentsByStatus(
            @PathVariable("status") final ShipmentStatus status,
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<Shipment> result = listShipmentsInPort.listShipmentsByStatus(status, new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                size,
                target -> linkTo(methodOn(ShipmentController.class).listShipmentsByStatus(status, target, size)).withSelfRel());
    }

    @GetMapping("/{shipmentNumber}")
    @Operation(summary = "Find a shipment by its number")
    @ApiResponse(
            responseCode = "404",
            description = SHIPMENT_NOT_FOUND_MESSAGE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ShipmentResource> getShipment(@PathVariable("shipmentNumber") final String shipmentNumber) {

        final Shipment shipment = getShipmentInPort.getShipment(shipmentNumber);
        if (Optional.ofNullable(shipment).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, SHIPMENT_NOT_FOUND_MESSAGE);
        }
        return toResourceModel(shipment);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new shipment")
    @ApiResponse(
            responseCode = "400",
            description = "orderNumber/carrier is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ShipmentResource> createShipment(@RequestBody final CreateShipmentResource resource) {

        if (resource == null) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ValidationConstants.INVALID_SHIPMENT_ORDER_NUMBER);
        }
        final String orderNumber = requireNonBlank(resource.orderNumber(), ValidationConstants.INVALID_SHIPMENT_ORDER_NUMBER);
        final String carrier = requireNonBlank(resource.carrier(), ValidationConstants.INVALID_SHIPMENT_CARRIER);
        return toResourceModel(shipmentWorkflow.createShipment(orderNumber, carrier));
    }

    @PostMapping("/{shipmentNumber}/advance")
    @Operation(summary = "Advance a shipment to its next status")
    public EntityModel<ShipmentResource> advanceShipmentStatus(@PathVariable("shipmentNumber") final String shipmentNumber) {

        final Shipment advanced = shipmentWorkflow.advanceShipment(shipmentNumber);
        return toResourceModel(advanced);
    }

    private String requireNonBlank(final String value, final String message) {

        if (value == null || value.isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private EntityModel<ShipmentResource> toResourceModel(final Shipment shipment) {

        final String shipmentNumber = shipment.getShipmentNumber();
        final EntityModel<ShipmentResource> model = EntityModel.of(
                shipmentWebMapper.mapToResource(shipment)
                        .orElseThrow(() -> new TechnicalProblemException("Shipment data is missing")),
                linkTo(methodOn(ShipmentController.class).getShipment(shipmentNumber)).withSelfRel());
        if (shipment.getStatus() != ShipmentStatus.DELIVERED) {

            model.add(
                    linkTo(methodOn(ShipmentController.class).advanceShipmentStatus(shipmentNumber)).withRel("advance-status"));
        }
        return model;
    }

    private static void validatePage(final int page, final int size) {
        if (page < 0 || size < 1 || size > PageQuery.MAX_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and " + PageQuery.MAX_SIZE);
        }
    }

    private PagedModel<EntityModel<ShipmentResource>> toPagedModel(
            final PagedResult<Shipment> result,
            final int page,
            final int size,
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
        if (page > 0) model.add(linkForPage.apply(page - 1).withRel(IanaLinkRelations.PREV));
        if (page < lastPage) model.add(linkForPage.apply(page + 1).withRel(IanaLinkRelations.NEXT));
        model.add(linkForPage.apply(lastPage).withRel(IanaLinkRelations.LAST));
        return model;
    }

}
