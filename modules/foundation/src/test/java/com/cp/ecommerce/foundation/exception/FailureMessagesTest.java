package com.cp.ecommerce.foundation.exception;

import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class FailureMessagesTest {

    private static final String REJECTED = "Key (email)=(buyer@example.com) already exists.";
    private static final String UNIQUE_VIOLATION = "23505";

    private static final String MASKED_RECIPIENT = "to [email]";

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
    void shouldReplaceEmailAddressesInOtherFailures() {

        final IllegalStateException exception = new IllegalStateException("mail to <buyer.one+tag@mail.example.co.uk> failed");

        assertThat(FailureMessages.describe(exception)).isEqualTo("mail to <[email]> failed");
        assertThat(FailureMessages.loggableCause(exception)).isNull();
        assertThat(FailureMessages.redact(null)).isNull();
    }

    @Test
    void shouldReplaceUnusualAndMalformedEmailAddresses() {

        assertThat(FailureMessages.redact("to user@localhost")).isEqualTo(MASKED_RECIPIENT);
        assertThat(FailureMessages.redact("to j\u00fcrgen@m\u00fcnchen.de")).isEqualTo(MASKED_RECIPIENT);
        assertThat(FailureMessages.redact("to o'brien@example.com.")).isEqualTo(MASKED_RECIPIENT);
        assertThat(FailureMessages.redact("to \"jane doe\"@example.com")).isEqualTo(MASKED_RECIPIENT);
        assertThat(FailureMessages.redact("Illegal address ``a@@b''")).isEqualTo("Illegal address ``[email]");
        assertThat(FailureMessages.redact("Key (email)=(buyer@example.com)")).isEqualTo("Key (email)=([email])");
        assertThat(FailureMessages.redact("email=buyer@example.com")).isEqualTo("email=[email]");
    }

    @Test
    void shouldKeepAnnotationsAndQuotedTextWithoutAnAddress() {

        assertThat(FailureMessages.redact("annotated with @Scheduled")).isEqualTo("annotated with @Scheduled");
        assertThat(FailureMessages.redact("template \"default\" failed")).isEqualTo("template \"default\" failed");
    }

    @Test
    void shouldReplacePhoneNumbers() {

        assertThat(FailureMessages.redact("call +48 123 456 789 now")).isEqualTo("call [phone] now");
        assertThat(FailureMessages.redact("call (+48) 600-700-800.")).isEqualTo("call [phone].");
        assertThat(FailureMessages.redact("call 600700800")).isEqualTo("call [phone]");
        assertThat(FailureMessages.redact("phones 555 555 555 (home)")).isEqualTo("phones [phone] (home)");
        assertThat(FailureMessages.redact("call 600700800!")).isEqualTo("call [phone]!");
        assertThat(FailureMessages.redact("call 600700800- now")).isEqualTo("call [phone]- now");
        assertThat(FailureMessages.redact("call +48 600 700 800-ext 12")).isEqualTo("call [phone]-ext 12");
    }

    @Test
    void shouldReplacePhoneListsAndLabelledPhones() {

        assertThat(FailureMessages.redact("phones 600700800,601701801")).isEqualTo("phones [phone],[phone]");
        assertThat(FailureMessages.redact("phones 600700800 601701801")).isEqualTo("phones [phone]");
        assertThat(FailureMessages.redact("phones 600 700 800 601 701 801")).isEqualTo("phones [phone]");
        assertThat(FailureMessages.redact("tel:600700800, phone:+48600700800")).isEqualTo("tel:[phone], phone:[phone]");
        assertThat(FailureMessages.redact("tel.:600 700 800")).isEqualTo("tel.:[phone]");
        assertThat(FailureMessages.redact("{\"phone\":600700800}")).isEqualTo("{\"phone\":[phone]}");
    }

    @Test
    void shouldKeepNumbersThatAreNotPhoneNumbers() {

        final String identifiers = "order f47ac10b-58cc-4372-a567-0e02b2c3d479-42, span 0123456789abcdef, 2026-11-11T10:00Z";
        assertThat(FailureMessages.redact(identifiers)).isEqualTo(identifiers);
        final String locations = "queue arn:aws:sqs:eu-central-1:123456789012:audit, iam::123456789012, /123456789012/a, ( ";
        assertThat(FailureMessages.redact(locations)).isEqualTo(locations);
        final String numbers = "attempt 3 of 12345678, total 1234567890.50, 1234567890,5, 12345678901234567, 600700800-x";
        assertThat(FailureMessages.redact(numbers)).isEqualTo(numbers);
        final String lists = "timestamp 1731340800000, values 1 2 3 4 5 6 7 8 9, at 2026-11-11 10:00, nr600700800";
        assertThat(FailureMessages.redact(lists)).isEqualTo(lists);
        assertThat(FailureMessages.redact("time 600700800:00")).isEqualTo("time 600700800:00");
        final String epochSeconds = "expired at 1731340800";
        assertThat(FailureMessages.redact(epochSeconds)).isEqualTo(epochSeconds);
        assertThat(FailureMessages.redact("call 5551234567 or 48600700800")).isEqualTo("call [phone] or [phone]");
        assertThat(FailureMessages.redact("tel 0612345678, 0221234567")).isEqualTo("tel [phone], [phone]");
        assertThat(FailureMessages.redact("id 600700800_a and 600700800/b")).isEqualTo("id 600700800_a and 600700800/b");
    }

    @Test
    void shouldKeepObjectIdentityStringsAndMaskEncodedAddresses() {

        final String identity = "rejected by java.util.concurrent.ThreadPoolExecutor@6d06d69c[Running]";
        assertThat(FailureMessages.redact(identity)).isEqualTo(identity);
        assertThat(FailureMessages.redact("GET /users?email=jane%40example.com")).isEqualTo("GET /users?email=[email]");
    }

    @Test
    void shouldDropTheStackTraceWhenASuppressedExceptionNamesAnAddress() {

        final IllegalStateException exception = new IllegalStateException("close failed");
        exception.addSuppressed(new RuntimeException("wrapper", new RuntimeException("mailbox jan@example.com is full")));

        assertThat(FailureMessages.loggableCause(exception)).isNull();
        final IllegalStateException clean = new IllegalStateException("close failed");
        clean.addSuppressed(new RuntimeException("socket closed"));
        assertThat(FailureMessages.loggableCause(clean)).isSameAs(clean);
    }

    @Test
    void shouldDropTheStackTraceWhenAnyCauseNamesAPhoneNumber() {

        final RuntimeException cause = new RuntimeException("courier rejected +48 600 700 800");

        assertThat(FailureMessages.loggableCause(new IllegalStateException("shipment failed", cause))).isNull();
    }

    @Test
    void shouldDropTheStackTraceWhenAnyCauseNamesAnEmailAddress() {

        final RuntimeException cause = new RuntimeException("Invalid Addresses: buyer@example.com");

        assertThat(FailureMessages.loggableCause(new IllegalStateException("send failed", cause))).isNull();
        final IllegalStateException withoutEmail = new IllegalStateException("send failed", new RuntimeException());
        assertThat(FailureMessages.loggableCause(withoutEmail)).isSameAs(withoutEmail);
    }

    @Test
    void shouldScanLongMessagesInLinearTime() {

        final String hostile = "a".repeat(200_000) + "@" + "b.".repeat(100_000) + "@".repeat(100_000) + "\"".repeat(100_000)
                + "1 ".repeat(100_000) + "a1".repeat(100_000) + "-1".repeat(100_000);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> FailureMessages.redact(hostile));
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
