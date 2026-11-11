package com.cp.ecommerce.adapter.web.exception;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseFailuresTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String STATEMENT_FAILED = "could not execute statement";

    @Test
    void shouldFindSqlExceptionInCausesAndSuppressedExceptions() {

        final SQLException sqlException = new SQLException("rejected", UNIQUE_VIOLATION);
        final RuntimeException suppressing = new RuntimeException("outer");
        suppressing.addSuppressed(sqlException);

        assertThat(DatabaseFailures.sqlException(new RuntimeException("outer", sqlException))).containsSame(sqlException);
        assertThat(DatabaseFailures.sqlException(suppressing)).containsSame(sqlException);
        final RuntimeException otherSuppressed = new RuntimeException("no database");
        otherSuppressed.addSuppressed(new IllegalStateException("cleanup failed"));
        assertThat(DatabaseFailures.sqlException(otherSuppressed)).isEmpty();
    }

    @Test
    void shouldRecognizeOnlyClientDataUniqueKeys() {

        assertThat(DatabaseFailures.isClientDataConflict(violation(UNIQUE_VIOLATION, "uq_product_sku"))).isTrue();
        final RuntimeException h2Named = violation(UNIQUE_VIOLATION, "PUBLIC.UQ_CATEGORY_SLUG_INDEX_1");
        assertThat(DatabaseFailures.isClientDataConflict(h2Named)).isTrue();
        assertThat(DatabaseFailures.isClientDataConflict(violation(UNIQUE_VIOLATION, "uk_order_order_number"))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(violation(UNIQUE_VIOLATION, null))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(violation("23502", "uq_product_sku"))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(new SQLException("unnamed", UNIQUE_VIOLATION))).isFalse();
    }

    @Test
    void shouldNameCausesAndStopOnCycles() {

        final RuntimeException first = new RuntimeException("first");
        final IllegalStateException second = new IllegalStateException("second", first);
        first.initCause(second);

        assertThat(DatabaseFailures.causeNames(second)).isEqualTo("IllegalStateException < RuntimeException");
        assertThat(DatabaseFailures.exceedsDepth(second)).isFalse();
    }

    @Test
    void shouldReportChainsLongerThanTheWalkLimit() {

        Exception exception = new SQLException("rejected", UNIQUE_VIOLATION);
        for (int depth = 0; depth < DatabaseFailures.MAX_CAUSE_DEPTH; depth++) {
            exception = new RuntimeException("wrapper", exception);
        }

        assertThat(DatabaseFailures.exceedsDepth(exception)).isTrue();
        assertThat(DatabaseFailures.sqlException(exception)).isEmpty();
    }

    private static RuntimeException violation(final String sqlState, final String constraintName) {

        final SQLException cause = new SQLException("rejected", sqlState);
        return new ConstraintViolationException(STATEMENT_FAILED, cause, constraintName);
    }
}
