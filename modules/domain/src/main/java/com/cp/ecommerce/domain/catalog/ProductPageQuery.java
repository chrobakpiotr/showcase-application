package com.cp.ecommerce.domain.catalog;

/**
 * Query parameters for a page of catalog products, ordered by name (ascending), optionally filtered down to a single category
 * and/or active-only products.
 *
 * <p>
 * Deliberately does not expose an arbitrary sort field, mirroring {@code com.cp.ecommerce.domain.order.PageQuery}'s reasoning:
 * the catalog browsing use case only ever needs "alphabetical by name", so keeping this fixed avoids exposing internal column
 * names as a sort-key API surface.
 */
public record ProductPageQuery(int page, int size, String categorySlug, boolean activeOnly) {

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 100;

    public ProductPageQuery {

        if (page < 0) {

            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {

            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

}
