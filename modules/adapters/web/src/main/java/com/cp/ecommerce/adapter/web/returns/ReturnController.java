package com.cp.ecommerce.adapter.web.returns;

import java.util.Optional;
import java.util.function.IntFunction;

import com.cp.ecommerce.adapter.web.returns.mapper.ReturnWebMapper;
import com.cp.ecommerce.adapter.web.returns.resource.RequestReturnResource;
import com.cp.ecommerce.adapter.web.returns.resource.ReturnRequestResource;
import com.cp.ecommerce.application.returns.ReturnWorkflow;
import com.cp.ecommerce.domain.returns.PageQuery;
import com.cp.ecommerce.domain.returns.PagedResult;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ListReturnsInPort;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * Controller serving the back-office returns / RMA API.
 *
 * <p>
 * Cross-bounded-context composition stays here, not in {@code domain.returns}: the controller validates the referenced
 * {@link Order}, computes the authoritative refund amount from the order line-item snapshot, and composes
 * {@link ManagePaymentInPort#refundPayment(String, String, BigDecimal)} and notification logging after moderation, matching
 * {@code OrderController#cancelOrder}.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/returns")
@Tag(name = "Returns", description = "Requesting, reading and moderating return / RMA requests")
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class ReturnController {

    private static final String RETURN_NOT_FOUND_MESSAGE = "Return request not found";

    private final GetReturnInPort getReturnInPort;

    private final ReturnWorkflow returnWorkflow;

    private final ListReturnsInPort listReturnsInPort;

    private final ReturnWebMapper returnWebMapper;

    @GetMapping
    @Operation(summary = "List return requests", description = "Newest first, page by page.")
    public PagedModel<EntityModel<ReturnRequestResource>> listReturns(
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<ReturnRequest> result = listReturnsInPort.listReturns(new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                target -> linkTo(methodOn(ReturnController.class).listReturns(target, size)).withSelfRel());
    }

    @GetMapping("/pending")
    @Operation(summary = "List pending return requests", description = "Oldest first, page by page.")
    public PagedModel<EntityModel<ReturnRequestResource>> listPendingReturns(
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<ReturnRequest> result = listReturnsInPort.listPendingReturns(new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                target -> linkTo(methodOn(ReturnController.class).listPendingReturns(target, size)).withSelfRel());
    }

    @GetMapping("/order/{orderNumber}")
    @Operation(summary = "List return requests for an order", description = "Newest first, page by page.")
    public PagedModel<EntityModel<ReturnRequestResource>> listReturnsForOrder(
            @PathVariable("orderNumber") final String orderNumber,
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @RequestParam(name = "size", defaultValue = "" + PageQuery.DEFAULT_SIZE) final int size) {
        validatePage(page, size);
        final PagedResult<ReturnRequest> result = listReturnsInPort.listReturnsForOrder(orderNumber, new PageQuery(page, size));
        return toPagedModel(
                result,
                page,
                target -> linkTo(methodOn(ReturnController.class).listReturnsForOrder(orderNumber, target, size))
                        .withSelfRel());
    }

    @GetMapping("/{returnNumber}")
    @Operation(summary = "Find a return request by its number")
    @ApiResponse(
            responseCode = "404",
            description = RETURN_NOT_FOUND_MESSAGE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ReturnRequestResource> getReturn(@PathVariable("returnNumber") final String returnNumber) {

        final ReturnRequest returnRequest = getReturnInPort.getReturn(returnNumber);
        if (Optional.ofNullable(returnRequest).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, RETURN_NOT_FOUND_MESSAGE);
        }
        return toResourceModel(returnRequest);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new return request")
    @ApiResponse(
            responseCode = "400",
            description = "orderNumber/sku/quantity/reason is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ReturnRequestResource> requestReturn(@RequestBody final RequestReturnResource resource) {

        if (resource == null) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ValidationConstants.INVALID_RETURN_ORDER_NUMBER);
        }
        final String orderNumber = requireNonBlank(resource.orderNumber(), ValidationConstants.INVALID_RETURN_ORDER_NUMBER);
        final String sku = requireNonBlank(resource.sku(), ValidationConstants.INVALID_RETURN_SKU);
        final int quantity = requirePositiveQuantity(resource.quantity());
        final String reason = requireNonBlank(resource.reason(), ValidationConstants.INVALID_RETURN_REASON);
        final ReturnRequest created = returnWorkflow.requestReturn(orderNumber, sku, quantity, reason);
        return toResourceModel(created);
    }

    @PostMapping("/{returnNumber}/approve")
    @Operation(summary = "Approve a return request and trigger payment refund")
    public EntityModel<ReturnRequestResource> approveReturn(@PathVariable("returnNumber") final String returnNumber) {

        final ReturnRequest refunded = returnWorkflow.approveReturn(returnNumber);
        return toResourceModel(refunded);
    }

    @PostMapping("/{returnNumber}/reject")
    @Operation(summary = "Reject a return request")
    public EntityModel<ReturnRequestResource> rejectReturn(@PathVariable("returnNumber") final String returnNumber) {

        final ReturnRequest rejected = returnWorkflow.rejectReturn(returnNumber);
        return toResourceModel(rejected);
    }

    private String requireNonBlank(final String value, final String message) {

        if (value == null || value.isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private int requirePositiveQuantity(final Integer quantity) {

        if (quantity == null || quantity < 1) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ValidationConstants.INVALID_RETURN_QUANTITY);
        }
        return quantity;
    }

    private EntityModel<ReturnRequestResource> toResourceModel(final ReturnRequest returnRequest) {

        final String returnNumber = returnRequest.getReturnNumber();
        final EntityModel<ReturnRequestResource> model = EntityModel.of(
                returnWebMapper.mapToResource(returnRequest)
                        .orElseThrow(() -> new TechnicalProblemException("Return request data is missing")),
                linkTo(methodOn(ReturnController.class).getReturn(returnNumber)).withSelfRel());
        if (returnRequest.getStatus() == ReturnStatus.REQUESTED) {

            model.add(linkTo(methodOn(ReturnController.class).approveReturn(returnNumber)).withRel("approve"));
            model.add(linkTo(methodOn(ReturnController.class).rejectReturn(returnNumber)).withRel("reject"));
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

    private PagedModel<EntityModel<ReturnRequestResource>> toPagedModel(
            final PagedResult<ReturnRequest> result,
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
