package com.cp.ecommerce.adapter.security.configuration;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Converts an authenticated {@link Jwt} issued by Keycloak into a Spring Security {@link AbstractAuthenticationToken}, mapping
 * both OAuth2 scopes and Keycloak realm roles (found in the {@code realm_access.roles} claim) to granted authorities.
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";

    private static final String ROLES_CLAIM = "roles";

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(final Jwt jwt) {

        final Collection<GrantedAuthority> authorities = Stream
                .concat(scopeAuthoritiesConverter.convert(jwt).stream(), extractRealmRoles(jwt).stream())
                .collect(Collectors.toSet());
        return new JwtAuthenticationToken(jwt, authorities);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(final Jwt jwt) {

        final Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        final List<String> roles = Optional.ofNullable(realmAccess)
                .map(claim -> claim.get(ROLES_CLAIM))
                .filter(List.class::isInstance)
                .map(claim -> (List<String>) claim)
                .orElseGet(List::of);

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

}
