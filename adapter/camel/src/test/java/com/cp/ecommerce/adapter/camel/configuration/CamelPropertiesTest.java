package com.cp.ecommerce.adapter.camel.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking {@link CamelProperties}'s default-value behavior.
 */
class CamelPropertiesTest {

    @Test
    void shouldApplyDefaultsWhenValuesAreNull() {

        final CamelProperties properties = new CamelProperties(null, null);

        assertThat(properties.domesticCountryCode()).isEqualTo("PL");
        assertThat(properties.notificationDirectory())
                .isEqualTo(System.getProperty("java.io.tmpdir") + "/ecommerce-camel/order-notifications");
    }

    @Test
    void shouldKeepExplicitValuesWhenProvided() {

        final CamelProperties properties = new CamelProperties("DE", "/custom/notifications");

        assertThat(properties.domesticCountryCode()).isEqualTo("DE");
        assertThat(properties.notificationDirectory()).isEqualTo("/custom/notifications");
    }

}
