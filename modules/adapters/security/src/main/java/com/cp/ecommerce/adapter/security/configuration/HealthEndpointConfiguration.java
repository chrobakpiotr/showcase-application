package com.cp.ecommerce.adapter.security.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class representing health endpoint configuration.
 */
@Configuration
public class HealthEndpointConfiguration implements WebMvcConfigurer {

    public static final String HEALTH_ENDPOINT = "/health";

    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {

        registry.addViewController(HEALTH_ENDPOINT).setViewName("forward:/actuator/health");
    }

}
