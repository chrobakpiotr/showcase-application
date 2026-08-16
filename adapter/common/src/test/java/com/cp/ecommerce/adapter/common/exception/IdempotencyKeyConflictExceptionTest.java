package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IdempotencyKeyConflictException}.
 */
class IdempotencyKeyConflictExceptionTest {

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final IdempotencyKeyConflictException exception = new IdempotencyKeyConflictException("idempotency key conflict");

        assertThat(exception).hasMessage("idempotency key conflict").isInstanceOf(BusinessRuleException.class);
    }

}
