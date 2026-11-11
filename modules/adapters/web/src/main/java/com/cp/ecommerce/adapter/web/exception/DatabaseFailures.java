package com.cp.ecommerce.adapter.web.exception;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Finds the database failure in an exception's cause chain without reading its message. */
final class DatabaseFailures {

    /** SQL standard SQLState for a unique-key violation, used by PostgreSQL and H2. */
    static final String UNIQUE_VIOLATION = "23505";

    static final int MAX_CAUSE_DEPTH = 16;

    private DatabaseFailures() {
    }

    /** The first {@link SQLException} among the causes and their suppressed exceptions. */
    static Optional<SQLException> sqlException(final Throwable exception) {

        for (final Throwable current : causes(exception)) {
            if (current instanceof SQLException sqlException) {
                return Optional.of(sqlException);
            }
            for (final Throwable suppressed : current.getSuppressed()) {
                if (suppressed instanceof SQLException sqlException) {
                    return Optional.of(sqlException);
                }
            }
        }
        return Optional.empty();
    }

    static boolean isUniqueViolation(final Throwable exception) {

        return sqlException(exception).map(SQLException::getSQLState).filter(UNIQUE_VIOLATION::equals).isPresent();
    }

    /** True when the cause chain is longer than the walk limit, so a database cause could not be ruled out. */
    static boolean exceedsDepth(final Throwable exception) {

        final List<Throwable> causes = causes(exception);
        return causes.size() == MAX_CAUSE_DEPTH && causes.get(MAX_CAUSE_DEPTH - 1).getCause() != null;
    }

    /** Simple class names of the cause chain, outermost first; class names carry no request data. */
    static String causeNames(final Throwable exception) {

        final List<String> names = new ArrayList<>();
        for (final Throwable current : causes(exception)) {
            names.add(current.getClass().getSimpleName());
        }
        return String.join(" < ", names);
    }

    private static List<Throwable> causes(final Throwable exception) {

        final List<Throwable> causes = new ArrayList<>();
        Throwable current = exception;
        while (current != null && causes.size() < MAX_CAUSE_DEPTH && !causes.contains(current)) {
            causes.add(current);
            current = current.getCause();
        }
        return causes;
    }
}
