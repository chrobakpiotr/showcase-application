package com.cp.ecommerce.foundation.exception;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureMessagesTest {

    private static final String REJECTED = "Key (email)=(buyer@example.com) already exists.";
    private static final String UNIQUE_VIOLATION = "23505";

    @Test
    void shouldDescribeDatabaseFailuresWithoutTheirMessages() {

        final SQLException cause = new SQLException(REJECTED, UNIQUE_VIOLATION);
        final RuntimeException exception = new RuntimeException("could not execute statement", cause);
        final String expected = "database error, SQLState=23505, causes=RuntimeException < SQLException, message omitted";

        assertThat(FailureMessages.describe(exception)).isEqualTo(expected);
        assertThat(FailureMessages.loggableCause(exception)).isNull();
    }

    @Test
    void shouldKeepTheMessageAndStackTraceOfOtherFailures() {

        final IllegalStateException exception = new IllegalStateException("payment gateway timed out");

        assertThat(FailureMessages.describe(exception)).isEqualTo("payment gateway timed out");
        assertThat(FailureMessages.loggableCause(exception)).isSameAs(exception);
    }

    @Test
    void shouldFindSqlExceptionInCausesAndSuppressedExceptions() {

        final SQLException sqlException = new SQLException(REJECTED, UNIQUE_VIOLATION);
        final RuntimeException suppressing = new RuntimeException("outer");
        suppressing.addSuppressed(sqlException);
        final RuntimeException otherSuppressed = new RuntimeException("no database");
        otherSuppressed.addSuppressed(new IllegalStateException("cleanup failed"));

        assertThat(FailureMessages.sqlException(new RuntimeException("outer", sqlException))).containsSame(sqlException);
        assertThat(FailureMessages.sqlException(suppressing)).containsSame(sqlException);
        assertThat(FailureMessages.sqlException(otherSuppressed)).isEmpty();
    }

    @Test
    void shouldNameCausesAndStopOnCycles() {

        final RuntimeException first = new RuntimeException("first");
        final IllegalStateException second = new IllegalStateException("second", first);
        first.initCause(second);

        assertThat(FailureMessages.causeNames(second)).isEqualTo("IllegalStateException < RuntimeException");
        assertThat(FailureMessages.exceedsDepth(second)).isFalse();
    }

    @Test
    void shouldOmitTheMessageWhenTheChainIsTooDeepToInspect() {

        Exception exception = new SQLException(REJECTED, UNIQUE_VIOLATION);
        for (int depth = 0; depth < FailureMessages.MAX_CAUSE_DEPTH; depth++) {
            exception = new RuntimeException("wrapper " + exception.getMessage(), exception);
        }

        assertThat(FailureMessages.exceedsDepth(exception)).isTrue();
        assertThat(FailureMessages.sqlException(exception)).isEmpty();
        assertThat(FailureMessages.describe(exception)).isEqualTo("cause chain longer than 16, message omitted");
        assertThat(FailureMessages.loggableCause(exception)).isNull();
    }
}
