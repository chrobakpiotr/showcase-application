package com.cp.ecommerce.adapter.web.shipments;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.shipments.mapper.ShipmentWebMapper;
import com.cp.ecommerce.adapter.web.shipments.resource.CreateShipmentResource;
import com.cp.ecommerce.adapter.web.shipments.resource.ShipmentResource;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.GetShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.ListShipmentsInPort;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
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

    private final CreateShipmentInPort createShipmentInPort;

    private final AdvanceShipmentStatusInPort advanceShipmentStatusInPort;

    private final GetShipmentInPort getShipmentInPort;

    private final ListShipmentsInPort listShipmentsInPort;

    private final ManageOrderUseCase manageOrderUseCase;

    private final SendNotificationInPort sendNotificationInPort;

    private final ShipmentWebMapper shipmentWebMapper;

    @GetMapping
    @Operation(summary = "List shipments", description = "Newest first. Empty list if there are none.")
    public CollectionModel<EntityModel<ShipmentResource>> listShipments() {

        return CollectionModel.of(
                listShipmentsInPort.listShipments().stream().map(this::toResourceModel).toList(),
                linkTo(methodOn(ShipmentController.class).listShipments()).withSelfRel());
    }

    @GetMapping("/order/{orderNumber}")
    @Operation(summary = "List shipments for an order", description = "Newest first. Empty list if there are none.")
    public CollectionModel<EntityModel<ShipmentResource>> listShipmentsForOrder(
            @PathVariable("orderNumber") final String orderNumber) {

        return CollectionModel.of(
                listShipmentsInPort.listShipmentsForOrder(orderNumber).stream().map(this::toResourceModel).toList(),
                linkTo(methodOn(ShipmentController.class).listShipmentsForOrder(orderNumber)).withSelfRel());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List shipments by status", description = "Newest first. Empty list if there are none.")
    public CollectionModel<EntityModel<ShipmentResource>> listShipmentsByStatus(
            @PathVariable("status") final ShipmentStatus status) {

        return CollectionModel.of(
                listShipmentsInPort.listShipmentsByStatus(status).stream().map(this::toResourceModel).toList(),
                linkTo(methodOn(ShipmentController.class).listShipmentsByStatus(status)).withSelfRel());
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
        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (order == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only CONFIRMED orders can be shipped");
        }
        return toResourceModel(createShipmentInPort.createShipment(orderNumber, carrier));
    }

    @PostMapping("/{shipmentNumber}/advance")
    @Operation(summary = "Advance a shipment to its next status")
    public EntityModel<ShipmentResource> advanceShipmentStatus(@PathVariable("shipmentNumber") final String shipmentNumber) {

        final Shipment advanced = advanceShipmentStatusInPort.advanceShipmentStatus(shipmentNumber);
        if (advanced == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, SHIPMENT_NOT_FOUND_MESSAGE);
        }
        if (advanced.getStatus() == ShipmentStatus.DISPATCHED) {

            sendShipmentDispatchedNotification(advanced);
        }
        if (advanced.getStatus() == ShipmentStatus.DELIVERED) {

            sendShipmentDeliveredNotification(advanced);
        }
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

    private void sendShipmentDispatchedNotification(final Shipment shipment) {

        sendNotificationInPort.sendNotification(
                findCustomerEmail(shipment.getOrderNumber()),
                NotificationType.SHIPMENT_DISPATCHED,
                "Shipment " + shipment.getShipmentNumber() + " dispatched",
                "Your shipment " + shipment.getShipmentNumber() + " was dispatched. Tracking number: "
                        + shipment.getTrackingNumber() + ".");
    }

    private void sendShipmentDeliveredNotification(final Shipment shipment) {

        sendNotificationInPort.sendNotification(
                findCustomerEmail(shipment.getOrderNumber()),
                NotificationType.SHIPMENT_DELIVERED,
                "Shipment " + shipment.getShipmentNumber() + " delivered",
                "Your shipment " + shipment.getShipmentNumber() + " was delivered.");
    }

    private String findCustomerEmail(final String orderNumber) {

        final Order order = manageOrderUseCase.findOrder(orderNumber);
        if (order == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return order.getCustomer().getContact().getEmail();
    }

}
