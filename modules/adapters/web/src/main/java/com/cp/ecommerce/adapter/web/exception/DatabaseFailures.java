package com.cp.ecommerce.adapter.web.exception;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.cp.ecommerce.foundation.exception.FailureMessages;

import org.hibernate.exception.ConstraintViolationException;

/** Classifies database failures in an exception's cause chain without reading their messages. */
final class DatabaseFailures {

    /** SQL standard SQLState for a unique-key violation, used by PostgreSQL and H2. */
    static final String UNIQUE_VIOLATION = "23505";

    /** Unique keys over client-supplied values; a violation of any other unique key is a server defect. */
    static final Set<String> CLIENT_DATA_CONSTRAINTS = Set.of(
            "UQ_PRODUCT_SKU",
            "UQ_CATEGORY_SLUG",
            "UK_IDEMPOTENCY_KEY",
            "UK_SHIPMENT_ORDER_NUMBER",
            "UK_PAYMENT_REFUND_RETURN_CONTINUATION_RETURN");

    private DatabaseFailures() {
    }

    static boolean isUniqueViolation(final Throwable exception) {

        final Optional<SQLException> sqlException = FailureMessages.sqlException(exception);
        return sqlException.isPresent() && UNIQUE_VIOLATION.equals(sqlException.get().getSQLState());
    }

    /** A unique-key violation of a constraint over client-supplied values, named by Hibernate in the cause chain. */
    static boolean isClientDataConflict(final Throwable exception) {

        if (!isUniqueViolation(exception)) {
            return false;
        }
        for (final Throwable current : FailureMessages.causes(exception)) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                final String name = violation.getConstraintName().toUpperCase(Locale.ROOT);
                return CLIENT_DATA_CONSTRAINTS.stream().anyMatch(name::contains);
            }
        }
        return false;
    }
}
