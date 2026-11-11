package com.cp.ecommerce.foundation.exception;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Describes failures for logs and stored errors; database messages echo rejected values, so only SQLState is kept. */
public final class FailureMessages {

    /** Causes inspected before a chain counts as too deep to rule out a database cause. */
    public static final int MAX_CAUSE_DEPTH = 16;

    private FailureMessages() {
    }

    /** A message safe to log or store: the original message unless a database cause is present or cannot be ruled out. */
    public static String describe(final Throwable exception) {

        final Optional<SQLException> sqlException = sqlException(exception);
        if (sqlException.isPresent()) {
            final String sqlState = sqlException.get().getSQLState();
            return "database error, SQLState=" + sqlState + ", causes=" + causeNames(exception) + ", message omitted";
        }
        if (exceedsDepth(exception)) {
            return "cause chain longer than " + MAX_CAUSE_DEPTH + ", message omitted";
        }
        return exception.getMessage();
    }

    /** The exception to attach to a log event for its stack trace, or {@code null} when its messages may hold data. */
    public static Throwable loggableCause(final Throwable exception) {

        return sqlException(exception).isPresent() || exceedsDepth(exception) ? null : exception;
    }

    /** The first {@link SQLException} among the causes and their suppressed exceptions. */
    public static Optional<SQLException> sqlException(final Throwable exception) {

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

    /** True when the cause chain is longer than the walk limit, so a database cause could not be ruled out. */
    public static boolean exceedsDepth(final Throwable exception) {

        final List<Throwable> causes = causes(exception);
        return causes.size() == MAX_CAUSE_DEPTH && causes.get(MAX_CAUSE_DEPTH - 1).getCause() != null;
    }

    /** Simple class names of the cause chain, outermost first; class names carry no request data. */
    public static String causeNames(final Throwable exception) {

        final List<String> names = new ArrayList<>();
        for (final Throwable current : causes(exception)) {
            names.add(current.getClass().getSimpleName());
        }
        return String.join(" < ", names);
    }

    /** The cause chain, outermost first, bounded by {@link #MAX_CAUSE_DEPTH} and stopping at a cycle. */
    public static List<Throwable> causes(final Throwable exception) {

        final List<Throwable> causes = new ArrayList<>();
        Throwable current = exception;
        while (current != null && causes.size() < MAX_CAUSE_DEPTH && !causes.contains(current)) {
            causes.add(current);
            current = current.getCause();
        }
        return causes;
    }
}
