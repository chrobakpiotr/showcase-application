package com.cp.ecommerce.adapter.camel.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for the Camel-based order notification routing. Bound from the {@code service.camel.*} namespace in
 * application properties.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "service.camel")
@Component
public class CamelProperties {

    /**
     * ISO country code considered "domestic" for order-fulfillment routing purposes; orders shipping to this country are routed
     * to the domestic fulfillment channel, all others to the international one.
     */
    private String domesticCountryCode = "PL";

    /**
     * Base directory under which routed order notifications are written as JSON files (one sub-directory per channel:
     * {@code audit}, {@code domestic}, {@code international}). Defaults to a location under the JVM's temp directory so the
     * showcase runs with zero external infrastructure.
     */
    private String notificationDirectory = System.getProperty("java.io.tmpdir") + "/ecommerce-camel/order-notifications";

}
