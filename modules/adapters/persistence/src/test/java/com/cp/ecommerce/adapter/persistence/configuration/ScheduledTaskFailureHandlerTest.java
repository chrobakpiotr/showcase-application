package com.cp.ecommerce.adapter.persistence.configuration;

import java.sql.SQLException;

import com.cp.ecommerce.adapter.common.utils.LogCapture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTaskFailureHandlerTest {

    private final ScheduledTaskFailureHandler handler = new ScheduledTaskFailureHandler();

    @Test
    void shouldLogDatabaseFailuresWithoutMessageOrStackTrace() {

        final SQLException cause = new SQLException("Key (email)=(buyer@example.com) already exists.", "23505");

        try (LogCapture logs = new LogCapture(ScheduledTaskFailureHandler.class)) {
            handler.handleError(new IllegalStateException("could not execute statement", cause));

            assertThat(logs.formattedMessages()).contains("SQLState=23505").doesNotContain("buyer@example.com");
            assertThat(logs.events()).singleElement().satisfies(event -> assertThat(event.getThrowableProxy()).isNull());
        }
    }

    @Test
    void shouldKeepMessageAndStackTraceOfOtherFailures() {

        try (LogCapture logs = new LogCapture(ScheduledTaskFailureHandler.class)) {
            handler.handleError(new IllegalStateException("broker unavailable"));

            assertThat(logs.formattedMessages()).contains("broker unavailable");
            assertThat(logs.events()).singleElement().satisfies(event -> assertThat(event.getThrowableProxy()).isNotNull());
        }
    }
}
