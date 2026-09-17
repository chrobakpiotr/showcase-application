package com.cp.ecommerce.adapter.web.catalog;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.catalog.mapper.ProductWebMapper;
import com.cp.ecommerce.adapter.web.catalog.metrics.ProductMetrics;
import com.cp.ecommerce.adapter.web.catalog.resource.ProductDetailsResource;
import com.cp.ecommerce.adapter.web.catalog.resource.ProductResource;
import com.cp.ecommerce.domain.catalog.CategoryNotFoundException;
import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;
import com.cp.ecommerce.domain.catalog.usecase.ListProductsUseCase;
import com.cp.ecommerce.domain.catalog.usecase.ManageProductUseCase;

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
 * Controller serving the functionality of the {@link Product} API.
 * <p>
 * {@code CATALOG_READ}/{@code CATALOG_WRITE} mirror the same back-office/operator authorization model as {@code ORDER_READ}/
 * {@code ORDER_WRITE} (see ADR 0017).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catalog - Products", description = "Browsing and managing catalog products")
public class ProductController {

    private final ListProductsUseCase listProductsUseCase;

    private final ManageProductUseCase manageProductUseCase;

    private final ProductWebMapper productWebMapper;

    private final ProductMetrics productMetrics;

    @GetMapping
    @Operation(
            summary = "List catalog products",
            description = "Returns a page of products ordered alphabetically by name, optionally filtered by category and/or "
                    + "active-only.")
    @ApiResponse(
            responseCode = "200",
            description = "Page of products",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = ProductDetailsResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "page is negative, or size is not between 1 and " + ProductPageQuery.MAX_SIZE,
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public PagedModel<EntityModel<ProductDetailsResource>> listProducts(
            @Parameter(description = "Zero-based page index") @RequestParam(name = "page", defaultValue = "0") final int page,
            @Parameter(description = "Page size") @RequestParam(
                    name = "size",
                    defaultValue = "" + ProductPageQuery.DEFAULT_SIZE) final int size,
            @Parameter(description = "Optional category slug filter") @RequestParam(
                    name = "category",
                    required = false) final String category,
            @Parameter(description = "Only include active products") @RequestParam(
                    name = "activeOnly",
                    defaultValue = "false") final boolean activeOnly) {

        if (page < 0 || size < 1 || size > ProductPageQuery.MAX_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and " + ProductPageQuery.MAX_SIZE);
        }
        final PagedResult<Product> result = listProductsUseCase
                .listProducts(new ProductPageQuery(page, size, category, activeOnly));
        final List<EntityModel<ProductDetailsResource>> content = result.content()
                .stream()
                .map(product -> toResourceWithLinks(product, product.getSku()))
                .toList();
        final PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(
                result.size(),
                result.page(),
                result.totalElements(),
                result.totalPages());
        final PagedModel<EntityModel<ProductDetailsResource>> pagedModel = PagedModel.of(
                content,
                metadata,
                linkTo(methodOn(ProductController.class).listProducts(page, size, category, activeOnly)).withSelfRel());
        final int lastPage = Math.max(result.totalPages() - 1, 0);
        pagedModel.add(
                linkTo(methodOn(ProductController.class).listProducts(0, size, category, activeOnly))
                        .withRel(IanaLinkRelations.FIRST));
        if (page > 0) {

            pagedModel.add(
                    linkTo(methodOn(ProductController.class).listProducts(page - 1, size, category, activeOnly))
                            .withRel(IanaLinkRelations.PREV));
        }
        if (page < lastPage) {

            pagedModel.add(
                    linkTo(methodOn(ProductController.class).listProducts(page + 1, size, category, activeOnly))
                            .withRel(IanaLinkRelations.NEXT));
        }
        pagedModel.add(
                linkTo(methodOn(ProductController.class).listProducts(lastPage, size, category, activeOnly))
                        .withRel(IanaLinkRelations.LAST));
        return pagedModel;
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Find a product by its SKU")
    @ApiResponse(
            responseCode = "200",
            description = "Product found",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = ProductDetailsResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ProductDetailsResource> findProduct(@PathVariable("sku") final String sku) {

        final Product product = manageProductUseCase.findProduct(sku);
        if (Optional.ofNullable(product).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return toResourceWithLinks(product, sku);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new catalog product", description = "Resolves categorySlug and generates a new SKU.")
    @ApiResponse(
            responseCode = "201",
            description = "Product successfully created",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = ProductDetailsResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Product data is missing or invalid, or the referenced category does not exist",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ProductDetailsResource> createProduct(@RequestBody final ProductResource productResource) {

        final Product productDraft = productWebMapper.mapToDomainObject(productResource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product data is missing"));
        final String categorySlug = productWebMapper.extractCategorySlug(productResource);
        final Product created;
        try {

            created = manageProductUseCase.createProduct(productDraft, categorySlug);
        } catch (final CategoryNotFoundException e) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        productMetrics.recordProductCreated();
        return toResourceWithLinks(created, created.getSku());
    }

    @PutMapping("/{sku}")
    @Operation(
            summary = "Update an existing catalog product",
            description = "Updates the mutable, commercial attributes of a product. Category is not changeable this way.")
    @ApiResponse(
            responseCode = "200",
            description = "Product successfully updated",
            content = @Content(
                    mediaType = "application/hal+json",
                    schema = @Schema(implementation = ProductDetailsResource.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public EntityModel<ProductDetailsResource> updateProduct(
            @PathVariable("sku") final String sku,
            @RequestBody final ProductResource productResource) {

        final Product update = productWebMapper.mapToDomainObject(productResource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product data is missing"));
        final Product updated = manageProductUseCase.updateProduct(sku, update);
        if (Optional.ofNullable(updated).isEmpty()) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        productMetrics.recordProductUpdated();
        return toResourceWithLinks(updated, sku);
    }

    private EntityModel<ProductDetailsResource> toResourceWithLinks(final Product product, final String sku) {

        final ProductDetailsResource resource = productWebMapper.mapToResource(product)
                .orElseThrow(() -> new TechnicalProblemException("Product data is missing"));
        return EntityModel.of(resource, linkTo(methodOn(ProductController.class).findProduct(sku)).withSelfRel());
    }

}
