package com.cp.ecommerce.adapter.security.authentication;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking resolution of the currently authenticated operator's identity from the security context.
 */
class CurrentOperatorProviderTest {

    private final transient CurrentOperatorProvider provider = new CurrentOperatorProvider();

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolvePreferredUsernameFromJwtAuthentication() {

        final Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "f47ac10b-58cc-4372-a567-0e02b2c3d479")
                .claim("preferred_username", "order-admin")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .build();
        // Mirrors KeycloakJwtAuthenticationConverter, which always uses the authorities-carrying constructor - the
        // only one of JwtAuthenticationToken's constructors that actually marks the token as authenticated.
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ORDER_READ"))));

        assertThat(provider.currentOperator()).contains("order-admin");
    }

    @Test
    void shouldReturnEmptyWhenNoAuthenticationIsPresent() {

        assertThat(provider.currentOperator()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAuthenticationIsNotAuthenticated() {

        final TestingAuthenticationToken unauthenticated = new TestingAuthenticationToken("principal", "credentials");
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        assertThat(provider.currentOperator()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAuthenticationIsNotJwtBased() {

        final TestingAuthenticationToken nonJwtAuthentication = new TestingAuthenticationToken("principal", "credentials");
        nonJwtAuthentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(nonJwtAuthentication);

        assertThat(provider.currentOperator()).isEmpty();
    }

}
