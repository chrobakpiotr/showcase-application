package com.cp.ecommerce.domain.catalog;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Category}.
 */
class CategoryTest {

    @Test
    void shouldPassValidationForValidCategory() {

        final Category category = TestDomainObjectFactory.validCategory();

        assertDoesNotThrow(category::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenNameIsBlank() {

        final Category category = Category.builder().name(" ").slug("electronics").build();

        assertThrows(DomainObjectValidationException.class, category::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenSlugIsBlank() {

        final Category category = Category.builder().name("Electronics").slug(" ").build();

        assertThrows(DomainObjectValidationException.class, category::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenSlugContainsUppercaseOrSpaces() {

        final Category category = Category.builder().name("Electronics").slug("Not A Valid Slug!").build();

        assertThrows(DomainObjectValidationException.class, category::assertValidationsEmpty);
    }

    @Test
    void shouldPassValidationForKebabCaseSlugWithNumbers() {

        final Category category = Category.builder().name("Electronics 2024").slug("electronics-2024").build();

        assertDoesNotThrow(category::assertValidationsEmpty);
    }

}
