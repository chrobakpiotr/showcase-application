package com.cp.ecommerce.adapter.security.utils;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.USER_AGENT;

/**
 * Test of {@link OutgoingHttpRequestInterceptor}.
 */
@ExtendWith(MockitoExtension.class)
public class OutgoingHttpRequestInterceptorTest {

    @InjectMocks
    private transient OutgoingHttpRequestInterceptor interceptor;

    @Mock
    private transient ClientHttpRequestExecution clientHttpRequestExecution;

    @Test
    public void shouldAddUserAgentToHeader() throws IOException {

        final MockClientHttpRequest request = new MockClientHttpRequest();
        interceptor.intercept(request, new byte[0], clientHttpRequestExecution);

        assertThat(request.getHeaders().get(USER_AGENT)).isNotNull();
        assertThat(request.getHeaders().get(USER_AGENT).size()).isNotZero();
        assertThat(request.getHeaders().get(USER_AGENT).get(0)).isEqualTo("e-commerce");
    }
}
