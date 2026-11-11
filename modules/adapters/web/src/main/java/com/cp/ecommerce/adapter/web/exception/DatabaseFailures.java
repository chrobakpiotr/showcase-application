package com.cp.ecommerce.adapter.web.exception;

import java.sql.SQLException;
import java.util.Optional;

/** Finds the database failure in an exception's cause chain without reading its message. */
final class DatabaseFailures {

    /** SQL standard SQLState for a unique-key violation, used by PostgreSQL and H2. */
    static final String UNIQUE_VIOLATION = "23505";

    private static final int MAX_CAUSE_DEPTH = 16;

    private DatabaseFailures() {
    }

    static Optional<SQLException> sqlException(final Throwable exception) {

        Throwable current = exception;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException sqlException) {
                return Optional.of(sqlException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    static boolean isUniqueViolation(final Throwable exception) {

        return sqlException(exception).map(SQLException::getSQLState).filter(UNIQUE_VIOLATION::equals).isPresent();
    }
}
