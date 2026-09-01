package com.cp.ecommerce.adapter.security.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Initial configuration class serving web security.
 */
@Configuration
@EnableWebSecurity
@Order(WebSecurityConfiguration.WEB_SECURITY_CONFIGURER_ADAPTER_ORDER)
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    public static final int WEB_SECURITY_CONFIGURER_ADAPTER_ORDER = 100;

    private static final String ORDER_API_PATH_MATCHER = "/api/order/**";

    private static final String H2_PATH_MATCHER = "/h2-console/**";

    private static final String[] OPEN_API_PATH_MATCHERS = { "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**" };

    private static final String ORDER_READ_ROLE = "ORDER_READ";

    private static final String ORDER_WRITE_ROLE = "ORDER_WRITE";

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {

        return (web) -> web.ignoring().requestMatchers(H2_PATH_MATCHER);
    }

    @Bean
    public SecurityFilterChain webSecurityFilterChain(final HttpSecurity http) {

        http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(OPEN_API_PATH_MATCHERS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, ORDER_API_PATH_MATCHER)
                        .hasRole(ORDER_READ_ROLE)
                        .requestMatchers(HttpMethod.POST, ORDER_API_PATH_MATCHER)
                        .hasRole(ORDER_WRITE_ROLE)
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
                .exceptionHandling(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                // Spring Security's defaults already add X-Content-Type-Options: nosniff, X-Frame-Options and
                // HSTS-over-HTTPS without any of this - the one thing genuinely missing is a Content-Security-Policy,
                // which Spring Security never sets a default for. 'unsafe-inline' on style-src is required by the
                // bundled Swagger UI (springdoc-openapi), which injects syntax-highlighting <style> tags at runtime;
                // everything else (this app's own Angular bundle plus Swagger UI's own JS) is served same-origin, so
                // script-src/default-src can stay locked to 'self' with no external CDNs allow-listed.
                .headers(
                        headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives(
                                        "default-src 'self'; " + "script-src 'self'; " + "style-src 'self' 'unsafe-inline'; "
                                                + "img-src 'self' data:; " + "font-src 'self' data:; " + "connect-src 'self'; "
                                                + "frame-ancestors 'none'; " + "base-uri 'self'; " + "form-action 'self'")));

        return http.build();
    }

}
