package com.cp.ecommerce.adapter.security.authentication;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves a human-readable identifier for the currently authenticated operator (the Keycloak {@code
 * preferred_username} claim), so callers elsewhere in the application - e.g. audit logging around sensitive order actions -
 * don't need to know that authentication happens via a Keycloak-issued JWT, or which claim carries a readable name.
 * <p>
 * See ADR 0017 for why the order API is authorized by operator role rather than per-customer ownership, and why logging the
 * acting operator's identity is the chosen accountability mechanism instead.
 */
@Component
public class CurrentOperatorProvider {

    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";

    public Optional<String> currentOperator() {

        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(authentication -> authentication.getToken().getClaimAsString(PREFERRED_USERNAME_CLAIM));
    }

}
