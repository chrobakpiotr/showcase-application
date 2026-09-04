package com.cp.ecommerce.adapter.security.configuration;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
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

    private static final String CATALOG_API_PATH_MATCHER = "/api/catalog/**";

    private static final String INVENTORY_API_PATH_MATCHER = "/api/inventory/**";

    // Unlike every other bounded context's API, shopping carts are customer-facing, and this application has no
    // persisted customer-account/login concept at all (see ADR 0027) - Keycloak roles here exist only for back-office
    // operators. Requiring a role would therefore make the cart unusable for its actual (anonymous) audience, so this
    // is intentionally permitAll rather than gated like ORDER/CATALOG/INVENTORY above. Declared explicitly (even
    // though it would already fall through to the anyRequest().permitAll() default below) purely for documentation.
    private static final String CART_API_PATH_MATCHER = "/api/cart/**";

    // The AI ops-analytics assistant endpoint (see ADR 0021) is logically read-only - it only ever queries the
    // order-analytics projection and remarks-triage classification counts, never mutates anything - but must be a POST
    // since the question is a free-text request body. Without this specific, narrower rule it would otherwise fall
    // through to the general POST /api/order/** rule below and incorrectly demand ORDER_WRITE. Declared before that
    // general rule: Spring Security's authorizeHttpRequests matches path rules in declaration order, first match wins.
    private static final String ANALYTICS_ASK_API_PATH_MATCHER = "/api/order/analytics/ask";

    private static final String H2_PATH_MATCHER = "/h2-console/**";

    private static final String[] OPEN_API_PATH_MATCHERS = { "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**" };

    private static final String ORDER_READ_ROLE = "ORDER_READ";

    private static final String ORDER_WRITE_ROLE = "ORDER_WRITE";

    private static final String CATALOG_READ_ROLE = "CATALOG_READ";

    private static final String CATALOG_WRITE_ROLE = "CATALOG_WRITE";

    private static final String INVENTORY_READ_ROLE = "INVENTORY_READ";

    private static final String INVENTORY_WRITE_ROLE = "INVENTORY_WRITE";

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    // The Angular frontend authenticates via a direct resource-owner-password-credentials grant against
    // Keycloak's token endpoint (see AuthService.login in the frontend module), so the browser issues an XHR
    // straight to Keycloak's own origin rather than routing through this backend - connect-src therefore needs
    // to allow-list that origin too, or the CSP silently blocks the login request itself. Reusing the same
    // browser-facing issuer-uri already used for "iss" claim validation keeps both in lockstep across profiles
    // (local/docker/k8s) instead of hand-maintaining a second, easily-forgotten Keycloak origin property.
    @Value("${security.oauth2.issuer-uri:http://localhost:8081/realms/ecommerce}")
    private String oauth2IssuerUri;

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
                        .requestMatchers(HttpMethod.POST, ANALYTICS_ASK_API_PATH_MATCHER)
                        .hasRole(ORDER_READ_ROLE)
                        .requestMatchers(HttpMethod.POST, ORDER_API_PATH_MATCHER)
                        .hasRole(ORDER_WRITE_ROLE)
                        .requestMatchers(HttpMethod.GET, CATALOG_API_PATH_MATCHER)
                        .hasRole(CATALOG_READ_ROLE)
                        .requestMatchers(HttpMethod.POST, CATALOG_API_PATH_MATCHER)
                        .hasRole(CATALOG_WRITE_ROLE)
                        .requestMatchers(HttpMethod.PUT, CATALOG_API_PATH_MATCHER)
                        .hasRole(CATALOG_WRITE_ROLE)
                        .requestMatchers(HttpMethod.GET, INVENTORY_API_PATH_MATCHER)
                        .hasRole(INVENTORY_READ_ROLE)
                        .requestMatchers(HttpMethod.POST, INVENTORY_API_PATH_MATCHER)
                        .hasRole(INVENTORY_WRITE_ROLE)
                        .requestMatchers(CART_API_PATH_MATCHER)
                        .permitAll()
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
                // script-src/default-src can stay locked to 'self' with no external CDNs allow-listed. connect-src
                // additionally allow-lists Keycloak's own origin (see oauth2IssuerUri field javadoc above) since the
                // frontend talks to it directly for login, not through this backend.
                .headers(
                        headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives(
                                        "default-src 'self'; " + "script-src 'self'; " + "style-src 'self' 'unsafe-inline'; "
                                                + "img-src 'self' data:; " + "font-src 'self' data:; " + "connect-src 'self' "
                                                + keycloakOrigin() + "; " + "frame-ancestors 'none'; " + "base-uri 'self'; "
                                                + "form-action 'self'")));

        return http.build();
    }

    /**
     * Extracts just the scheme+host+port ("origin") from the configured issuer URI, since a CSP source expression must not
     * include a path (e.g. Keycloak's own "/realms/ecommerce" suffix would otherwise be sent verbatim to the browser, which
     * silently ignores it as an invalid source and falls back to blocking the request).
     */
    private String keycloakOrigin() {

        final URI issuerUri = URI.create(oauth2IssuerUri);
        return issuerUri.getScheme() + "://" + issuerUri.getAuthority();
    }

}
