package com.cp.ecommerce.adapter.security.configuration;

import com.cp.ecommerce.adapter.SecurityTestConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Integration test verifying that the {@link RestTemplateConfiguration}'s {@link RestTemplate} bean, together with the
 * {@link com.cp.ecommerce.adapter.security.utils.OutgoingHttpRequestInterceptor}, produces the expected outgoing HTTP traffic
 * against a real HTTP server stubbed with WireMock.
 */
@SpringBootTest
@Import(SecurityTestConfiguration.class)
class RestTemplateWireMockTest {

    private static final String PING_PATH = "/ping";

    private static final String FAILING_PATH = "/failing";

    private static final String RESPONSE_BODY = "pong";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Autowired
    private transient RestTemplate restTemplate;

    @Test
    void shouldSendUserAgentHeaderAndReceiveStubbedResponse() {

        wireMock.stubFor(
                get(urlEqualTo(PING_PATH)).willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody(RESPONSE_BODY)));

        final ResponseEntity<String> response = restTemplate.getForEntity(wireMock.baseUrl() + PING_PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(RESPONSE_BODY);
        wireMock.verify(getRequestedFor(urlEqualTo(PING_PATH)).withHeader(USER_AGENT, equalTo("e-commerce")));
    }

    @Test
    void shouldPropagateServerErrorsFromDownstreamService() {

        wireMock.stubFor(get(urlEqualTo(FAILING_PATH)).willReturn(serverError()));

        assertThatThrownBy(() -> restTemplate.getForEntity(wireMock.baseUrl() + FAILING_PATH, String.class))
                .isInstanceOf(HttpServerErrorException.class);
    }

}
