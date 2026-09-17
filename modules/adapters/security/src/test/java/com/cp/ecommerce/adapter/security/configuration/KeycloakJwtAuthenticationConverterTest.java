package com.cp.ecommerce.adapter.security.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking Keycloak specific JWT to Spring Security authorities conversion.
 */
class KeycloakJwtAuthenticationConverterTest {

    private final transient KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void shouldMapRealmRolesAndScopesToAuthorities() {

        final Jwt jwt = buildJwt(Map.of("roles", List.of("order_read", "ORDER_WRITE")), "order:manage");

        final List<String> authorities = converter.convert(jwt)
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_ORDER_READ", "ROLE_ORDER_WRITE", "SCOPE_order:manage");
    }

    @Test
    void shouldReturnNoAuthoritiesWhenRealmAccessClaimIsMissing() {

        final Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .build();

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }

    private Jwt buildJwt(final Map<String, Object> realmAccess, final String scope) {

        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .claim("realm_access", realmAccess)
                .claim("scope", scope)
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60))
                .build();
    }

}
