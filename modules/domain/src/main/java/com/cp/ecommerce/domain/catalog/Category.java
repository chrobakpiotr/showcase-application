package com.cp.ecommerce.domain.catalog;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Representation of a product category domain object - the top-level grouping products are browsed/filtered by (e.g.
 * "electronics", "books"). Deliberately flat (no parent/child category tree): a showcase catalog has no real need for nested
 * categories, and a flat model keeps {@code FindCategoriesOutPort}/the catalog browsing page simple.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Category extends ValidDomainObject<Category> {

    Long id;

    @NotBlank(message = ValidationConstants.INVALID_CATEGORY_NAME)
    @Size(max = ValidationConstants.CATEGORY_NAME_MAX, message = ValidationConstants.INVALID_CATEGORY_NAME)
    String name;

    // Lowercase-kebab-case only: the slug is a public, stable identifier used in the product listing filter
    // (GET /api/catalog/products?category=...) and, unlike the numeric id, is safe to expose/bookmark.
    @NotBlank(message = ValidationConstants.INVALID_CATEGORY_SLUG)
    @Size(max = ValidationConstants.CATEGORY_SLUG_MAX, message = ValidationConstants.INVALID_CATEGORY_SLUG)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = ValidationConstants.INVALID_CATEGORY_SLUG)
    String slug;

    public static Category.CategoryBuilder builder() {

        return new Category.CategoryBuilder() {

            @Override
            public Category build() {

                return super.build().validate();
            }
        };
    }

}
