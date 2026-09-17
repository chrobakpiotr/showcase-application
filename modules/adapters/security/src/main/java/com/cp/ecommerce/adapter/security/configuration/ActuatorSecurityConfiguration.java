package com.cp.ecommerce.adapter.security.configuration;

import com.cp.ecommerce.adapter.security.utils.ManagementUserProperties;

import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Configuration class representing actuator's endpoints configuration.
 */
@Configuration
@ComponentScan("com.cp.ecommerce.adapter.security")
@EnableConfigurationProperties(ManagementUserProperties.class)
@Order(ActuatorSecurityConfiguration.BEFORE_DEFAULT_WEB_SECURITY_CONFIGURER_ADAPTER)
@RequiredArgsConstructor
public class ActuatorSecurityConfiguration {

    public static final int BEFORE_DEFAULT_WEB_SECURITY_CONFIGURER_ADAPTER = WebSecurityConfiguration.WEB_SECURITY_CONFIGURER_ADAPTER_ORDER
            - 1;

    private static final String ACTUATOR = "ACTUATOR";

    private final ManagementUserProperties properties;

    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(final HttpSecurity http) {

        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(
                        authorize -> authorize
                                .requestMatchers(
                                        EndpointRequest.to(
                                                HealthEndpoint.class,
                                                InfoEndpoint.class,
                                                MetricsEndpoint.class,
                                                PrometheusScrapeEndpoint.class))
                                .permitAll()
                                .requestMatchers(EndpointRequest.toAnyEndpoint())
                                .hasRole(ACTUATOR))
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {

        final InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername(properties.name()).password(properties.password()).roles(ACTUATOR).build());
        return manager;
    }

}
