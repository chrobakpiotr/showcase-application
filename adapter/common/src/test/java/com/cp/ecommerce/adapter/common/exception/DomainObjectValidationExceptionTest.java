package com.cp.ecommerce.adapter.common.exception;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DomainObjectValidationException}.
 */
class DomainObjectValidationExceptionTest {

    @Test
    void shouldExposeMessageAndConstraintViolationsPassedToConstructor() {

        final Set<ConstraintViolation<?>> violations = Set.of();

        final DomainObjectValidationException exception = new DomainObjectValidationException("validation failed", violations);

        assertThat(exception).hasMessage("validation failed");
        assertThat(exception.getConstraintViolations()).isEqualTo(violations);
    }

}
