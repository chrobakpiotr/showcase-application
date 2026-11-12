package com.cp.ecommerce.foundation.exception;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Describes failures for logs and stored errors without database messages or email addresses, which identify people. */
public final class FailureMessages {

    /** Causes inspected before a chain counts as too deep to rule out a database cause. */
    public static final int MAX_CAUSE_DEPTH = 16;

    /** Replacement for an email address in a failure message. */
    public static final String EMAIL_PLACEHOLDER = "[email]";

    // A match may only start where a local part can start, and quantifiers are possessive, so a scan stays linear.
    private static final String LOCAL = "[A-Za-z0-9._%+-]";

    private static final String DOMAIN = "@[A-Za-z0-9-]++(?:\\.[A-Za-z0-9-]++)++";

    private static final Pattern EMAIL = Pattern.compile("(?<!" + LOCAL + ")" + LOCAL + "++" + DOMAIN);

    private FailureMessages() {
    }

    /** A message safe to log or store: database failures by SQLState, other messages with email addresses replaced. */
    public static String describe(final Throwable exception) {

        final Optional<SQLException> sqlException = sqlException(exception);
        if (sqlException.isPresent()) {
            final String sqlState = sqlException.get().getSQLState();
            return "database error, SQLState=" + sqlState + ", causes=" + causeNames(exception) + ", message omitted";
        }
        if (exceedsDepth(exception)) {
            return "cause chain longer than " + MAX_CAUSE_DEPTH + ", message omitted";
        }
        return redactEmails(exception.getMessage());
    }

    /** The message with every email address replaced by {@link #EMAIL_PLACEHOLDER}; {@code null} stays {@code null}. */
    public static String redactEmails(final String message) {

        return message == null ? null : EMAIL.matcher(message).replaceAll(EMAIL_PLACEHOLDER);
    }

    /** The exception to attach to a log event for its stack trace, or {@code null} when its messages may hold data. */
    public static Throwable loggableCause(final Throwable exception) {

        return sqlException(exception).isPresent() || exceedsDepth(exception) || containsEmail(exception) ? null : exception;
    }

    private static boolean containsEmail(final Throwable exception) {

        for (final Throwable current : causes(exception)) {
            if (current.getMessage() != null && EMAIL.matcher(current.getMessage()).find()) {
                return true;
            }
        }
        return false;
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
