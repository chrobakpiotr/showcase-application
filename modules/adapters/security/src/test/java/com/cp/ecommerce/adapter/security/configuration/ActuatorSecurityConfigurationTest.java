package com.cp.ecommerce.adapter.security.configuration;

import com.cp.ecommerce.adapter.SecurityTestConfiguration;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.BDDAssertions.then;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import static com.cp.ecommerce.adapter.security.configuration.ActuatorSecurityConfigurationTest.PROPERTY_MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE;
import static com.cp.ecommerce.adapter.security.configuration.ActuatorSecurityConfigurationTest.PROPERTY_MANAGEMENT_TRACE_HTTP_ENABLED_FALSE;
import static com.cp.ecommerce.adapter.security.configuration.ActuatorSecurityConfigurationTest.PROPERTY_MANAGEMENT_USER_NAME_ADMIN;
import static com.cp.ecommerce.adapter.security.configuration.ActuatorSecurityConfigurationTest.PROPERTY_MANAGEMENT_USER_PASSWORD_ADMIN;

/**
 * Test class checking actuator security's behavior.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                PROPERTY_MANAGEMENT_USER_NAME_ADMIN,
                PROPERTY_MANAGEMENT_USER_PASSWORD_ADMIN,
                PROPERTY_MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE,
                PROPERTY_MANAGEMENT_TRACE_HTTP_ENABLED_FALSE })
@Import(SecurityTestConfiguration.class)
class ActuatorSecurityConfigurationTest {

    static final String PROPERTY_MANAGEMENT_USER_NAME_ADMIN = "management.user.name=admin";

    static final String PROPERTY_MANAGEMENT_USER_PASSWORD_ADMIN = "management.user.password={noop}admin";

    static final String PROPERTY_MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "management.endpoints.web.exposure.include=*";

    static final String PROPERTY_MANAGEMENT_TRACE_HTTP_ENABLED_FALSE = "management.trace.http.enabled=false";

    private static final String LOCALHOST_URL = "http://localhost:";

    @Autowired
    private transient RestTemplateBuilder restTemplateBuilder;

    @LocalManagementPort
    private transient int localManagementPort;

    @ParameterizedTest
    @ValueSource(strings = { "/actuator/info", "/actuator/health", "/actuator/metrics" })
    void shouldHaveUnsecuredEndpoint(final String endpoint) {

        final TestRestTemplate testRestTemplate = new TestRestTemplate(createRootUri());
        final ResponseEntity<String> result = testRestTemplate.getForEntity(endpoint, String.class);
        then(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private RestTemplateBuilder createRootUri() {

        return restTemplateBuilder.rootUri(LOCALHOST_URL + localManagementPort);
    }

}
