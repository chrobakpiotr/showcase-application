package com.cp.ecommerce.adapter.web.coupon;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.coupon.mapper.CouponWebMapper;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponDetailsResource;
import com.cp.ecommerce.adapter.web.coupon.resource.CouponResource;
import com.cp.ecommerce.domain.coupon.Coupon;
import com.cp.ecommerce.domain.coupon.CouponPageQuery;
import com.cp.ecommerce.domain.coupon.PagedCoupons;
import com.cp.ecommerce.domain.coupon.port.incoming.CreateCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.GetCouponInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.ListCouponsInPort;
import com.cp.ecommerce.domain.coupon.port.incoming.ManageCouponInPort;

import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 * Controller serving the back-office coupon administration API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/coupons")
@Tag(name = "Coupons", description = "Creating, reading and managing discount coupons")
public class CouponController {

    private static final String COUPON_NOT_FOUND_MESSAGE = "Coupon not found";

    private final CreateCouponInPort createCouponInPort;

    private final ManageCouponInPort manageCouponInPort;

    private final GetCouponInPort getCouponInPort;

    private final ListCouponsInPort listCouponsInPort;

    private final CouponWebMapper couponWebMapper;

    @GetMapping
    @Operation(summary = "List coupons", description = "Returns a page of coupons ordered alphabetically by code.")
    @ApiResponse(
            responseCode = "200",
            description = "Page of coupons",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = CouponDetailsResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "page is negative, or size is not between 1 and " + CouponPageQuery.MAX_SIZE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public PagedModel<EntityModel<CouponDetailsResource>> listCoupons(
            @Parameter(description = "Zero-based page index") @RequestParam(name = "page", defaultValue = "0") final int page,
            @Parameter(description = "Page size") @RequestParam(
                    name = "size",
                    defaultValue = "" + CouponPageQuery.DEFAULT_SIZE) final int size,
            @Parameter(description = "Only include active coupons") @RequestParam(
                    name = "activeOnly",
                    required = false) final Boolean activeOnly) {

        if (page < 0 || size < 1 || size > CouponPageQuery.MAX_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and " + CouponPageQuery.MAX_SIZE);
        }
        final PagedCoupons result = listCouponsInPort.listCoupons(new CouponPageQuery(page, size, activeOnly));
        final List<EntityModel<CouponDetailsResource>> content = result.content()
                .stream()
                .map(coupon -> toResourceWithLinks(coupon, coupon.getCode()))
                .toList();
        final PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                result.size(),
                result.page(),
                result.totalElements(),
                result.totalPages());
        final PagedModel<EntityModel<CouponDetailsResource>> pagedModel = PagedModel.of(
                content,
                metadata,
                linkTo(methodOn(CouponController.class).listCoupons(page, size, activeOnly)).withSelfRel());
        final int lastPage = Math.max(result.totalPages() - 1, 0);
        pagedModel.add(
                linkTo(methodOn(CouponController.class).listCoupons(0, size, activeOnly)).withRel(IanaLinkRelations.FIRST));
        if (page > 0) {

            pagedModel.add(
                    linkTo(methodOn(CouponController.class).listCoupons(page - 1, size, activeOnly))
                            .withRel(IanaLinkRelations.PREV));
        }
        if (page < lastPage) {

            pagedModel.add(
                    linkTo(methodOn(CouponController.class).listCoupons(page + 1, size, activeOnly))
                            .withRel(IanaLinkRelations.NEXT));
        }
        pagedModel.add(
                linkTo(methodOn(CouponController.class).listCoupons(lastPage, size, activeOnly))
                        .withRel(IanaLinkRelations.LAST));
        return pagedModel;
    }

    @GetMapping("/{code}")
    @Operation(summary = "Find a coupon by code")
    @ApiResponse(
            responseCode = "200",
            description = "Coupon found",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = CouponDetailsResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = COUPON_NOT_FOUND_MESSAGE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<CouponDetailsResource> getCoupon(@PathVariable("code") final String code) {

        final Coupon coupon = getCouponInPort.getCoupon(code);
        if (Optional.ofNullable(coupon).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COUPON_NOT_FOUND_MESSAGE);
        }
        return toResourceWithLinks(coupon, coupon.getCode());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new coupon")
    public EntityModel<CouponDetailsResource> createCoupon(@RequestBody final CouponResource resource) {

        final Coupon draft = couponWebMapper.mapToDomainObject(resource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon data is missing"));
        final Coupon created = createCouponInPort.createCoupon(draft);
        return toResourceWithLinks(created, created.getCode());
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update an existing coupon")
    public EntityModel<CouponDetailsResource> updateCoupon(
            @PathVariable("code") final String code,
            @RequestBody final CouponResource resource) {

        final Coupon update = couponWebMapper.mapToDomainObject(resource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupon data is missing"));
        final Coupon coupon = manageCouponInPort.updateCoupon(code, update);
        if (coupon == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COUPON_NOT_FOUND_MESSAGE);
        }
        return toResourceWithLinks(coupon, coupon.getCode());
    }

    @PostMapping("/{code}/activate")
    @Operation(summary = "Activate a coupon")
    public EntityModel<CouponDetailsResource> activateCoupon(@PathVariable("code") final String code) {

        final Coupon coupon = manageCouponInPort.activateCoupon(code);
        if (coupon == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COUPON_NOT_FOUND_MESSAGE);
        }
        return toResourceWithLinks(coupon, coupon.getCode());
    }

    @PostMapping("/{code}/deactivate")
    @Operation(summary = "Deactivate a coupon")
    public EntityModel<CouponDetailsResource> deactivateCoupon(@PathVariable("code") final String code) {

        final Coupon coupon = manageCouponInPort.deactivateCoupon(code);
        if (coupon == null) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, COUPON_NOT_FOUND_MESSAGE);
        }
        return toResourceWithLinks(coupon, coupon.getCode());
    }

    private EntityModel<CouponDetailsResource> toResourceWithLinks(final Coupon coupon, final String code) {

        final CouponDetailsResource resource = couponWebMapper.mapToResource(coupon)
                .orElseThrow(() -> new TechnicalProblemException("Coupon data is missing"));
        return EntityModel.of(resource, linkTo(methodOn(CouponController.class).getCoupon(code)).withSelfRel());
    }

}
