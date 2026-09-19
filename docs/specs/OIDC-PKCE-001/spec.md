# OIDC-PKCE-001 - browser authentication with Authorization Code + PKCE

## Intent

Replace the browser Resource Owner Password Credentials flow with the standard
OpenID Connect Authorization Code flow using PKCE S256 and the official Keycloak
JavaScript adapter.

The backend remains a stateless OAuth2 Resource Server and continues to validate
bearer tokens exactly as before.

## Current problem

Before R16:

- the Angular application collected the operator password itself;
- the browser called the Keycloak token endpoint with `grant_type=password`;
- the access token was persisted in `sessionStorage`;
- Keycloak enabled direct access grants and disabled the standard browser flow;
- the client accepted wildcard redirect URIs and wildcard web origins.

This puts credential handling inside the SPA and unnecessarily exposes a bearer
token to persistent browser storage.

## Decision

- Use the official `keycloak-js` package.
- Use standard Authorization Code flow.
- Require PKCE code challenge method `S256` in Keycloak client configuration.
- Disable direct access grants.
- Keep the client public because a SPA cannot protect a client secret.
- Store access, ID and refresh tokens only in the in-memory Keycloak adapter.
- Do not mirror tokens into localStorage, sessionStorage, IndexedDB or cookies.
- Bootstrap Keycloak before normal application navigation using `check-sso`.
- Disable the session-status iframe for predictable cross-browser behavior.
- Refresh a token before protected API calls when it has less than 30 seconds
  validity.
- If refresh fails, clear local authentication state and send no stale bearer
  token.
- Login redirects to Keycloak and returns to the originally requested internal
  Angular route.
- Logout clears local state and performs OIDC logout, returning to `/login`.
- Reject external/open-redirect values supplied as `returnUrl`.
- Preserve the existing resource-server role model and R08 fail-closed API
  boundary.

## Keycloak client policy

`ecommerce-app` remains a public OIDC client with:

- `standardFlowEnabled=true`;
- `directAccessGrantsEnabled=false`;
- `implicitFlowEnabled=false`;
- `pkce.code.challenge.method=S256`;
- explicit localhost redirect URIs for the application and Angular development
  server;
- explicit localhost web origins;
- post-logout redirect URIs inherited from the explicit redirect allowlist.

Wildcard redirect URIs and web origins are forbidden.

## Session policy

The browser does not persist OIDC tokens.

A browser reload can restore authentication only through Keycloak SSO state during
`check-sso`; it does not restore a bearer token from application storage.

The Keycloak adapter owns token expiry and refresh-token use. The application
interceptor asks the adapter for a valid access token before protected API calls.

## Acceptance criteria

- AC-001: no password grant remains in frontend production code.
- AC-002: the SPA never stores an OIDC token in Web Storage.
- AC-003: Keycloak standard flow is enabled and direct access grants are disabled.
- AC-004: Keycloak client configuration requires PKCE S256.
- AC-005: Keycloak redirect URIs and web origins are explicit and contain no `*`
  as a standalone wildcard.
- AC-006: login redirects through Keycloak rather than posting credentials from
  Angular.
- AC-007: an authenticated callback restores username and realm roles.
- AC-008: initialization/callback failure leaves the SPA unauthenticated.
- AC-009: the originally requested internal route is used as the post-login
  redirect.
- AC-010: an external, protocol-relative or path-escaping `returnUrl` falls back
  to `/dashboard`.
- AC-011: the interceptor refreshes a nearly expired token before protected API
  requests.
- AC-012: refresh failure clears auth state and does not attach a stale token.
- AC-013: logout performs Keycloak logout and returns to the application login
  page.
- AC-014: the existing `order-viewer` account can authenticate through the same
  flow and retains read-only roles.
- AC-015: backend JWT/role enforcement is unchanged.
- AC-016: focused frontend tests, frontend build/lint and full repository gates
  pass.
- AC-017: a Playwright auth-flow test exercises the real Keycloak login page,
  return-route preservation and logout in the containerized E2E environment.

## Out of scope

- introducing customer identities or ownership;
- BFF/cookie-session architecture;
- changing backend JWT validation;
- R17 role-aware Angular route guards/capability map;
- redesigning Keycloak themes;
- persisting refresh tokens in application-controlled browser storage.
