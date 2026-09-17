package com.cp.ecommerce.adapter.security.utils;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpHeaders.USER_AGENT;

/**
 * Outgoing request interceptor.
 */
@Component
@RequiredArgsConstructor
public class OutgoingHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final String USER_AGENT_VALUE = "e-commerce";

    @Override
    public ClientHttpResponse intercept(
            final HttpRequest request,
            final byte[] body,
            final ClientHttpRequestExecution execution) throws IOException {

        addUserAgent(request);
        return execution.execute(request, body);
    }

    private void addUserAgent(final HttpRequest request) {

        request.getHeaders().add(USER_AGENT, USER_AGENT_VALUE);
    }

}
