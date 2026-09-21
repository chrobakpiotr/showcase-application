package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ProblemDetailFactory {

    private static final String ERROR_ID_PROPERTY = "errorId";
    private static final String TRACE_ID_PROPERTY = "traceId";

    private final ObjectProvider<Tracer> tracerProvider;

    ProblemDetailFactory(final ObjectProvider<Tracer> tracerProvider) {

        this.tracerProvider = tracerProvider;
    }

    ProblemDetail create(
            final Exception exception,
            final HttpStatusCode status,
            final URI type,
            final String title,
            final String detail) {

        final String errorId = UUID.randomUUID().toString();
        final String traceId = currentTraceId();
        log.error(
                "{} [errorId={}, traceId={}]: {}",
                exception.getClass().getSimpleName(),
                errorId,
                traceId != null ? traceId : "none",
                exception.getMessage());

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(type);
        if (title != null) {
            problemDetail.setTitle(title);
        }
        problemDetail.setProperty(ERROR_ID_PROPERTY, errorId);
        if (traceId != null) {
            problemDetail.setProperty(TRACE_ID_PROPERTY, traceId);
        }
        return problemDetail;
    }

    private String currentTraceId() {

        final Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return null;
        }
        final Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }
}
