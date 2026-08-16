package com.cp.ecommerce.adapter.camel.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CamelProperties} as a constructor-bound {@code @ConfigurationProperties} bean.
 *
 * <p>
 * {@link CamelProperties} is a record with no default (no-arg) constructor, so it cannot be picked up via plain
 * {@code @Component} scanning (that mechanism resolves the record's canonical constructor as regular dependency injection, not
 * property binding). {@link EnableConfigurationProperties} registers it as a {@code ConfigurationPropertiesBean} instead, which
 * explicitly binds the {@code service.camel.*} namespace onto the record's canonical constructor.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(CamelProperties.class)
public class CamelPropertiesConfiguration {

}
