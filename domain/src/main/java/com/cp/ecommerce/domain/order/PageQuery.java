package com.cp.ecommerce.domain.order;

/**
 * Query parameters for a page of orders, ordered by creation date (most recent first).
 *
 * <p>
 * Deliberately does not expose an arbitrary sort field: the API only ever needs "most recent first", so keeping this fixed
 * avoids exposing internal column names as a sort-key API surface.
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 100;

    public PageQuery {

        if (page < 0) {

            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {

            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

}
