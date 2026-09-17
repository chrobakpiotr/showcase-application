package com.cp.ecommerce.adapter.common.validation;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ValidDomainObject}.
 */
class ValidDomainObjectTest {

    @Test
    void shouldHaveNoViolationsWhenObjectIsValid() {

        final SampleDomainObject validated = new SampleDomainObject("present").validate();

        assertThat(validated.getViolations()).isEmpty();
        validated.assertValidationsEmpty();
    }

    @Test
    void shouldCollectViolationsWhenObjectIsInvalid() {

        final SampleDomainObject validated = new SampleDomainObject(" ").validate();

        assertThat(validated.getViolations()).isNotEmpty();
    }

    @Test
    void shouldThrowWhenAssertingValidationsEmptyOnInvalidObject() {

        final SampleDomainObject invalid = new SampleDomainObject(" ").validate();

        assertThatThrownBy(invalid::assertValidationsEmpty).isInstanceOf(DomainObjectValidationException.class);
    }

    @Getter
    private static final class SampleDomainObject extends ValidDomainObject<SampleDomainObject> {

        @NotBlank
        private final String name;

        private SampleDomainObject(final String name) {

            this.name = name;
        }

    }

}
