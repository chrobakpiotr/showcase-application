package com.cp.ecommerce.adapter.persistence.configuration;

import com.cp.ecommerce.foundation.exception.FailureMessages;

import org.springframework.util.ErrorHandler;

import lombok.extern.slf4j.Slf4j;

/** Logs failures escaping {@code @Scheduled} methods; database failures appear by SQLState only, without stack trace. */
@Slf4j
final class ScheduledTaskFailureHandler implements ErrorHandler {

    @Override
    public void handleError(final Throwable failure) {

        // A null trailing argument is ignored by SLF4J, so database failures are logged without their stack trace.
        log.error(
                "Unexpected error occurred in scheduled task: {}",
                FailureMessages.describe(failure),
                FailureMessages.loggableCause(failure));
    }
}
