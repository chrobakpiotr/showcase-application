package com.cp.ecommerce.adapter.persistence.configuration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Persistence configuration class.
 */
@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.cp.ecommerce.adapter.persistence")
@EnableScheduling
@EntityScan(basePackages = "com.cp.ecommerce.adapter.persistence")
@ComponentScan(basePackages = { "com.cp.ecommerce.adapter.persistence", "com.cp.ecommerce.adapter.common.resilience" })
public class PersistenceConfiguration {

}
