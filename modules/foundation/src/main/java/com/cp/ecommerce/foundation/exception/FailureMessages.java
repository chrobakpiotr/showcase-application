package com.cp.ecommerce.foundation.exception;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Describes failures for logs and stored errors without database messages, email addresses or phone numbers. */
public final class FailureMessages {

    /** Causes inspected before a chain counts as too deep to rule out a database cause. */
    public static final int MAX_CAUSE_DEPTH = 16;

    /** Replacement for an email address in a failure message. */
    public static final String EMAIL_PLACEHOLDER = "[email]";

    /** Replacement for a phone number in a failure message. */
    public static final String PHONE_PLACEHOLDER = "[phone]";

    private static final int PHONE_MIN_DIGITS = 9;

    private static final int PHONE_MAX_DIGITS = 15;

    // Characters that end a token. Any token with text before an '@' counts as an address, so quoted, non-ASCII and
    // malformed addresses are masked too. A match starts only at a token start and quantifiers are possessive, so a scan
    // stays linear; a quoted local part is bounded to 64 characters.
    private static final String DELIMITERS = "\\s<>()\\[\\]{},;\"`=";

    private static final Pattern EMAIL = Pattern.compile("\"[^\"\\r\\n]{0,64}+\"@[^" + DELIMITERS + "]*+|(?<![^" + DELIMITERS
            + "])[^" + DELIMITERS + "@]++@[^" + DELIMITERS + "]*+");

    // A run of digits with phone separators that is not part of a word, an identifier, a time, a path or a number.
    private static final Pattern PHONE_CANDIDATE = Pattern.compile("(?<![\\p{L}\\p{N}_+\\-:./@])\\(?+\\+?+[0-9(][0-9 ()-]*+");

    private FailureMessages() {
    }

    /** A message safe to log or store: database failures by SQLState, other messages with personal data replaced. */
    public static String describe(final Throwable exception) {

        final Optional<SQLException> sqlException = sqlException(exception);
        if (sqlException.isPresent()) {
            final String sqlState = sqlException.get().getSQLState();
            return "database error, SQLState=" + sqlState + ", causes=" + causeNames(exception) + ", message omitted";
        }
        if (exceedsDepth(exception)) {
            return "cause chain longer than " + MAX_CAUSE_DEPTH + ", message omitted";
        }
        return redact(exception.getMessage());
    }

    /**
     * The message with email addresses replaced by {@link #EMAIL_PLACEHOLDER} and phone numbers by
     * {@link #PHONE_PLACEHOLDER}; {@code null} stays {@code null}. Names and street addresses cannot be recognised, so
     * callers must keep them out of messages.
     */
    public static String redact(final String message) {

        return message == null ? null : redactPhones(EMAIL.matcher(message).replaceAll(EMAIL_PLACEHOLDER));
    }

    private static String redactPhones(final String message) {

        final Matcher candidate = PHONE_CANDIDATE.matcher(message);
        final StringBuilder redacted = new StringBuilder(message.length());
        int copied = 0;
        while (candidate.find()) {
            final int end = trimmedEnd(message, candidate.start(), candidate.end());
            if (isPhone(message, candidate.start(), end, candidate.end())) {
                redacted.append(message, copied, candidate.start()).append(PHONE_PLACEHOLDER);
                copied = end;
            }
        }
        return redacted.append(message, copied, message.length()).toString();
    }

    private static int trimmedEnd(final String message, final int start, final int end) {

        int trimmed = end;
        while (trimmed > start && !Character.isDigit(message.charAt(trimmed - 1))) {
            trimmed--;
        }
        return trimmed;
    }

    private static boolean isPhone(final String message, final int start, final int trimmedEnd, final int runEnd) {

        final long digits = message.substring(start, trimmedEnd).chars().filter(Character::isDigit).count();
        return digits >= PHONE_MIN_DIGITS && digits <= PHONE_MAX_DIGITS && !attachedToWord(message, trimmedEnd, runEnd);
    }

    // A run that ends in a digit or dash right before a letter, ':' or a decimal point belongs to a larger token.
    private static boolean attachedToWord(final String message, final int trimmedEnd, final int runEnd) {

        if (runEnd == message.length() || trimmedEnd < runEnd && message.charAt(runEnd - 1) != '-') {
            return false;
        }
        final char next = message.charAt(runEnd);
        final boolean decimal = (next == '.' || next == ',') && runEnd + 1 < message.length()
                && Character.isDigit(message.charAt(runEnd + 1));
        return Character.isLetter(next) || next == '_' || next == ':' || next == '/' || next == '@' || decimal;
    }

    /** The exception to attach to a log event for its stack trace, or {@code null} when its messages may hold data. */
    public static Throwable loggableCause(final Throwable exception) {

        return sqlException(exception).isPresent() || exceedsDepth(exception) || containsPersonalData(exception) ? null
                : exception;
    }

    private static boolean containsPersonalData(final Throwable exception) {

        for (final Throwable current : causes(exception)) {
            final String message = current.getMessage();
            if (message != null && !message.equals(redact(message))) {
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
