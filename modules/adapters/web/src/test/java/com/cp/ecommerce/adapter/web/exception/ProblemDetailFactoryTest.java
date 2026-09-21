package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ProblemDetail;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

class ProblemDetailFactoryTest {

    private static final URI TYPE = URI.create("urn:problem-type:test");

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);

    private final Tracer tracer = mock(Tracer.class);

    private final ProblemDetailFactory factory = new ProblemDetailFactory(tracerProvider);

    @Test
    void shouldAddCurrentTraceIdExtensionMember() {

        final Span span = mock(Span.class);
        final TraceContext traceContext = mock(TraceContext.class);
        given(tracerProvider.getIfAvailable()).willReturn(tracer);
        given(tracer.currentSpan()).willReturn(span);
        given(span.context()).willReturn(traceContext);
        given(traceContext.traceId()).willReturn("0123456789abcdef0123456789abcdef");

        final ProblemDetail problemDetail = createProblem();

        assertThat(problemDetail.getProperties()).containsEntry("traceId", "0123456789abcdef0123456789abcdef");
    }

    @Test
    void shouldNotInventTraceIdWithoutTracer() {

        assertThat(createProblem().getProperties()).doesNotContainKey("traceId");
    }

    @Test
    void shouldNotExposeTraceIdWhenTracerHasNoCurrentSpan() {

        given(tracerProvider.getIfAvailable()).willReturn(tracer);

        assertThat(createProblem().getProperties()).doesNotContainKey("traceId");
    }

    private ProblemDetail createProblem() {

        return factory.create(new RuntimeException("message"), INTERNAL_SERVER_ERROR, TYPE, "Internal Server Error", "message");
    }
}
