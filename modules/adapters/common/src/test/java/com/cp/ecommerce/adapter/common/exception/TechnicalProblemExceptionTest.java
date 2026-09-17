package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TechnicalProblemException}.
 */
class TechnicalProblemExceptionTest {

    private static final String MESSAGE = "technical problem";

    @Test
    void shouldExposeMessagePassedToConstructor() {

        final TechnicalProblemException exception = new TechnicalProblemException(MESSAGE);

        assertThat(exception).hasMessage(MESSAGE);
    }

    @Test
    void shouldExposeMessageAndCausePassedToConstructor() {

        final Throwable cause = new IllegalStateException("root cause");

        final TechnicalProblemException exception = new TechnicalProblemException(MESSAGE, cause);

        assertThat(exception).hasMessage(MESSAGE).hasCause(cause);
    }

}
