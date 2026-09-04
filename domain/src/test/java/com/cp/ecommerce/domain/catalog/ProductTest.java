package com.cp.ecommerce.domain.catalog;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Product}.
 */
class ProductTest {

    @Test
    void shouldPassValidationForValidProduct() {

        final Product product = TestDomainObjectFactory.validProduct();

        assertDoesNotThrow(product::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultActiveToTrue() {

        final Product product = Product.builder()
                .name("Wireless Mouse")
                .category(TestDomainObjectFactory.validCategory())
                .unitPrice(new BigDecimal("29.99"))
                .build();

        assertTrue(product.isActive());
    }

    @Test
    void shouldFailValidationWhenNameIsBlank() {

        final Product product = Product.builder()
                .name(" ")
                .category(TestDomainObjectFactory.validCategory())
                .unitPrice(new BigDecimal("29.99"))
                .build();

        assertThrows(DomainObjectValidationException.class, product::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenCategoryIsMissing() {

        final Product product = Product.builder().name("Wireless Mouse").unitPrice(new BigDecimal("29.99")).build();

        assertThrows(DomainObjectValidationException.class, product::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenUnitPriceIsMissing() {

        final Product product = Product.builder()
                .name("Wireless Mouse")
                .category(TestDomainObjectFactory.validCategory())
                .build();

        assertThrows(DomainObjectValidationException.class, product::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenUnitPriceIsZeroOrNegative() {

        final Product product = Product.builder()
                .name("Wireless Mouse")
                .category(TestDomainObjectFactory.validCategory())
                .unitPrice(BigDecimal.ZERO)
                .build();

        assertThrows(DomainObjectValidationException.class, product::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenNestedCategoryIsInvalid() {

        // Proves Product.category's @Valid cascade actually reaches Category's own constraints.
        final Product product = Product.builder()
                .name("Wireless Mouse")
                .category(Category.builder().name("Electronics").slug("Not A Valid Slug!").build())
                .unitPrice(new BigDecimal("29.99"))
                .build();

        assertThrows(DomainObjectValidationException.class, product::assertValidationsEmpty);
    }

}
