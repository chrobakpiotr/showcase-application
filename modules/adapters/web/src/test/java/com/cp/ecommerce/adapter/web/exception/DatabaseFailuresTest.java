package com.cp.ecommerce.adapter.web.exception;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseFailuresTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String STATEMENT_FAILED = "could not execute statement";

    @Test
    void shouldRecognizeOnlyClientDataUniqueKeys() {

        assertThat(DatabaseFailures.isClientDataConflict(violation(UNIQUE_VIOLATION, "uq_product_sku"))).isTrue();
        final RuntimeException h2Named = violation(UNIQUE_VIOLATION, "PUBLIC.UQ_CATEGORY_SLUG_INDEX_1");
        assertThat(DatabaseFailures.isClientDataConflict(h2Named)).isTrue();
        assertThat(DatabaseFailures.isClientDataConflict(violation(UNIQUE_VIOLATION, "uk_order_order_number"))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(violation(UNIQUE_VIOLATION, null))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(violation("23502", "uq_product_sku"))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(new SQLException("unnamed", UNIQUE_VIOLATION))).isFalse();
        assertThat(DatabaseFailures.isClientDataConflict(new IllegalStateException("no database"))).isFalse();
    }

    private static RuntimeException violation(final String sqlState, final String constraintName) {

        final SQLException cause = new SQLException("rejected", sqlState);
        return new ConstraintViolationException(STATEMENT_FAILED, cause, constraintName);
    }
}
