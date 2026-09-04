package com.cp.ecommerce.domain.catalog;

/**
 * Thrown when a product creation request references a {@link Category} slug that does not exist. Deliberately a distinct,
 * lightweight unchecked exception rather than reusing {@code DomainObjectValidationException}: this is not a bean-validation
 * failure on the {@link Product} itself, but a foreign-key-style reference to another aggregate that turned out to be dangling
 * - the web layer maps it to {@code 400 Bad Request} specifically, the same outcome a bean-validation failure gets, but for a
 * different underlying reason worth its own type.
 */
public class CategoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CategoryNotFoundException(final String slug) {

        super("No category found for slug: " + slug);
    }

}
