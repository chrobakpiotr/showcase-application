package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BusinessRuleException}.
 */
class BusinessRuleExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final BusinessRuleException exception = new BusinessRuleException("business rule violated");

        assertThat(exception).hasMessage("business rule violated");
    }

}
