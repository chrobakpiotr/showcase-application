package com.cp.ecommerce.adapter.security.configuration;

import java.time.Duration;

import com.cp.ecommerce.adapter.security.utils.OutgoingHttpRequestInterceptor;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Class that creates the RestTemplate bean for the application context.
 */
@Configuration
class RestTemplateConfiguration {

    private static final Duration TIMEOUT = Duration.ofMillis(10000);

    @Bean
    public RestTemplate restTemplate(final OutgoingHttpRequestInterceptor outgoingHttpRequestInterceptor) {

        return new RestTemplateBuilder().connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .interceptors(outgoingHttpRequestInterceptor)
                .build();
    }
}
