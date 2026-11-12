package com.cp.ecommerce.adapter.web.exception;

import com.cp.ecommerce.adapter.common.utils.LogCapture;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import io.micrometer.tracing.Tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MalformedRequestHandlerTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);

    @Test
    void shouldLogOnlyTheCauseClassesOfAnUnreadableRequest() {

        final IllegalStateException parserFailure = new IllegalStateException("Unrecognized token 'jan.kowalski'");
        final HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error: Unrecognized token 'jan.kowalski'",
                parserFailure,
                new MockHttpInputMessage(new byte[0]));

        try (LogCapture logs = new LogCapture(ProblemDetailFactory.class)) {
            final ProblemDetail problem = new GlobalExceptionHandler(tracerProvider).malformedRequest(exception);

            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getDetail()).isEqualTo("Malformed request");
            assertThat(logs.formattedMessages()).startsWith("UnreadableRequest")
                    .endsWith(": HttpMessageNotReadableException < IllegalStateException")
                    .doesNotContain("jan.kowalski");
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        }
    }
}
