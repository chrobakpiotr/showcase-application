package com.cp.ecommerce.adapter.camel.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Camel-based order notification routing. Bound from the {@code service.camel.*} namespace in
 * application properties via constructor binding.
 *
 * @param domesticCountryCode ISO country code considered "domestic" for order-fulfillment routing purposes; orders shipping to
 *            this country are routed to the domestic fulfillment channel, all others to the international one. Defaults to
 *            {@code PL}.
 * @param notificationDirectory Base directory under which routed order notifications are written as JSON files (one
 *            sub-directory per channel: {@code audit}, {@code domestic}, {@code international}). Defaults to a location under
 *            the JVM's temp directory so the showcase runs with zero external infrastructure.
 */
@ConfigurationProperties(prefix = "service.camel")
public record CamelProperties(String domesticCountryCode, String notificationDirectory) {

    public CamelProperties {

        if (domesticCountryCode == null) {
            domesticCountryCode = "PL";
        }
        if (notificationDirectory == null) {
            notificationDirectory = System.getProperty("java.io.tmpdir") + "/ecommerce-camel/order-notifications";
        }
    }

}
