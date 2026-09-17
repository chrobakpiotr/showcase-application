package com.cp.ecommerce.adapter.web.catalog;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.adapter.web.catalog.mapper.CategoryWebMapper;
import com.cp.ecommerce.adapter.web.catalog.resource.CategoryResource;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.usecase.CreateCategoryUseCase;
import com.cp.ecommerce.domain.catalog.usecase.ListCategoriesUseCase;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Controller serving the functionality of the product {@link Category} API.
 * <p>
 * {@code CATALOG_READ}/{@code CATALOG_WRITE} mirror the same back-office/operator authorization model as {@code ORDER_READ}/
 * {@code ORDER_WRITE} (see ADR 0017) - browsing categories is public-facing-equivalent read access, creating one is a
 * catalog-management action.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/catalog/categories")
@Tag(name = "Catalog - Categories", description = "Browsing and managing product categories")
public class CategoryController {

    private final ListCategoriesUseCase listCategoriesUseCase;

    private final CreateCategoryUseCase createCategoryUseCase;

    private final CategoryWebMapper categoryWebMapper;

    @GetMapping
    @Operation(summary = "List all product categories")
    @ApiResponse(
            responseCode = "200",
            description = "All categories, ordered alphabetically by name",
            content = @Content(schema = @Schema(implementation = CategoryResource.class)))
    public List<CategoryResource> listCategories() {

        return listCategoriesUseCase.listCategories()
                .stream()
                .map(categoryWebMapper::mapToResource)
                .flatMap(Optional::stream)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new product category")
    @ApiResponse(
            responseCode = "201",
            description = "Category successfully created",
            content = @Content(schema = @Schema(implementation = CategoryResource.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Category data is missing or invalid",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    public CategoryResource createCategory(@RequestBody final CategoryResource categoryResource) {

        final Category categoryDraft = categoryWebMapper.mapToDomainObject(categoryResource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category data is missing"));
        categoryDraft.assertValidationsEmpty();
        final Category created = createCategoryUseCase.createCategory(categoryDraft);
        return categoryWebMapper.mapToResource(created)
                .orElseThrow(() -> new TechnicalProblemException("Category data is missing"));
    }

}
