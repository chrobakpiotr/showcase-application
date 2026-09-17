package com.cp.ecommerce.adapter;

import com.cp.ecommerce.adapter.security.configuration.HealthEndpointConfiguration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Spring boot application needed for properly loading spring boot context in test configuration.
 */
@SpringBootApplication
@Import(HealthEndpointConfiguration.class)
public class SpringBootWebTestApplication {

}
