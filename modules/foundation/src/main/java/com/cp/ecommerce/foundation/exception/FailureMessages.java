package com.cp.ecommerce.foundation.exception;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
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

    private static final int PLAIN_PHONE_MAX_DIGITS = 11;

    private static final int DECIMAL_MAX_DIGITS = 3;

    // Any token with text before an '@' counts as an address, so quoted, non-ASCII and malformed addresses are masked.
    // A match starts only at a token start and quantifiers are possessive, so a scan stays linear.
    private static final String TOKEN_CHAR = "[^\\s<>()\\[\\]{},;\"`=]";

    private static final String LOCAL_CHAR = "[^\\s<>()\\[\\]{},;\"`=@]";

    private static final String QUOTED_ADDRESS = "\"[^\"\\r\\n]{0,64}+\"@" + TOKEN_CHAR + "*+";

    private static final String ADDRESS = "(?<!" + TOKEN_CHAR + ")" + LOCAL_CHAR + "++@" + TOKEN_CHAR + "*+";

    private static final Pattern EMAIL = Pattern.compile(QUOTED_ADDRESS + "|" + ADDRESS);

    private static final Pattern URL_ENCODED_AT = Pattern.compile("%40", Pattern.CASE_INSENSITIVE);

    // Object.toString() of a class, such as com.example.Pool@6d06d69c, is a diagnostic, not an address.
    private static final Pattern IDENTITY_HASH = Pattern.compile("[A-Za-z_$][\\w$]*+(?:\\.[A-Za-z_$][\\w$]*+)++@[0-9a-f]{1,8}");

    // A phone run may not continue a word, identifier, path, number, or a ':' or '.' that follows a digit (ARNs, times).
    private static final String PHONE_START = "(?<![\\p{L}\\p{N}_+\\-/@])(?<![\\p{N}:][:.])";

    private static final Pattern PHONE_CANDIDATE = Pattern.compile(PHONE_START + "\\(?+\\+?+[0-9(][0-9 ()-]*+");

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

    /** The message with email addresses and phone numbers replaced; names and street addresses are not recognised. */
    public static String redact(final String message) {

        if (message == null) {
            return null;
        }
        final String decoded = URL_ENCODED_AT.matcher(message).replaceAll("@");
        return redactPhones(EMAIL.matcher(decoded).replaceAll(FailureMessages::emailReplacement));
    }

    private static String emailReplacement(final MatchResult match) {

        final String token = match.group();
        return IDENTITY_HASH.matcher(token).matches() ? Matcher.quoteReplacement(token) : EMAIL_PLACEHOLDER;
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

        final String run = message.substring(start, trimmedEnd);
        final boolean formatted = run.chars().anyMatch(character -> " ()+-".indexOf(character) >= 0);
        if (!digitGroupsLookLikeAPhone(run) || attachedToWord(message, trimmedEnd, runEnd, formatted)) {
            return false;
        }
        final long digits = run.chars().filter(Character::isDigit).count();
        if (!formatted) {
            return digits >= PHONE_MIN_DIGITS && digits <= PLAIN_PHONE_MAX_DIGITS;
        }
        // More than one spaced number in a row, such as two phones in one field, is masked as a whole.
        return digits >= PHONE_MIN_DIGITS && (digits <= PHONE_MAX_DIGITS || run.indexOf(' ') >= 0);
    }

    // Phone groups have at least two digits, except a country or area code after '+' or '('; "1 2 3" is a list.
    private static boolean digitGroupsLookLikeAPhone(final String run) {

        int groupLength = 0;
        boolean prefixed = false;
        for (int index = 0; index <= run.length(); index++) {
            final char character = index < run.length() ? run.charAt(index) : ' ';
            if (Character.isDigit(character)) {
                groupLength++;
            } else {
                if (groupLength == 1 && !prefixed) {
                    return false;
                }
                groupLength = 0;
                prefixed = character == '+' || character == '(';
            }
        }
        return true;
    }

    // A run that ends in a digit, or a plain run ending in a dash, right before a letter, ':', '/' or a decimal part
    // belongs to a larger token such as an identifier, a time or an amount.
    private static boolean attachedToWord(final String text, final int trimmedEnd, final int runEnd, final boolean formatted) {

        if (runEnd == text.length()) {
            return false;
        }
        final char last = text.charAt(runEnd - 1);
        if (trimmedEnd < runEnd && (last != '-' || formatted)) {
            return false;
        }
        final char next = text.charAt(runEnd);
        return Character.isLetter(next) || next == '_' || next == ':' || next == '/' || isDecimalPart(text, runEnd);
    }

    private static boolean isDecimalPart(final String message, final int separator) {

        final char character = message.charAt(separator);
        if (character != '.' && character != ',') {
            return false;
        }
        int digits = 0;
        int index = separator + 1;
        while (index < message.length() && digits <= DECIMAL_MAX_DIGITS && Character.isDigit(message.charAt(index))) {
            digits++;
            index++;
        }
        return digits >= 1 && digits <= DECIMAL_MAX_DIGITS;
    }

    /** The exception to attach to a log event for its stack trace, or {@code null} when its messages may hold data. */
    public static Throwable loggableCause(final Throwable exception) {

        if (sqlException(exception).isPresent() || exceedsDepth(exception)) {
            return null;
        }
        return containsPersonalData(exception) ? null : exception;
    }

    private static boolean containsPersonalData(final Throwable exception) {

        for (final Throwable current : causes(exception)) {
            if (holdsPersonalData(current) || suppressedHoldPersonalData(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean suppressedHoldPersonalData(final Throwable exception) {

        for (final Throwable suppressed : exception.getSuppressed()) {
            for (final Throwable nested : causes(suppressed)) {
                if (holdsPersonalData(nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean holdsPersonalData(final Throwable exception) {

        final String message = exception.getMessage();
        return message != null && !message.equals(redact(message));
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
