package com.cp.ecommerce.adapter.camel.configuration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking {@link CamelPropertiesConfiguration} registers constructor-bound {@link CamelProperties}.
 */
class CamelPropertiesConfigurationTest {

    @Test
    void shouldEnableCamelPropertiesConstructorBinding() {

        final CamelPropertiesConfiguration configuration = new CamelPropertiesConfiguration();

        final EnableConfigurationProperties annotation = configuration.getClass()
                .getAnnotation(EnableConfigurationProperties.class);
        assertThat(annotation.value()).containsExactly(CamelProperties.class);
    }

}
