# 0045. Browser authentication uses Authorization Code with PKCE and memory-only tokens

## Context

The backend already uses Keycloak-issued JWTs as a stateless OAuth2 Resource
Server. The browser authentication mechanism, however, used the Resource Owner
Password Credentials grant: Angular collected username/password, POSTed them to
the token endpoint and persisted the resulting access token in `sessionStorage`.

That flow gives the SPA direct access to user credentials and leaves a bearer token
available to any script that can read Web Storage.

The Keycloak client also enabled direct access grants, disabled standard flow and
allowed wildcard redirect URIs/web origins.

## Decision

Use the official `keycloak-js` adapter for the browser.

The SPA uses OIDC Authorization Code flow with PKCE S256. Keycloak is the only
surface that receives the operator password.

`ecommerce-app` remains a public client but:

- standard flow is enabled;
- direct access grants are disabled;
- implicit flow remains disabled;
- PKCE S256 is required;
- redirect URIs and web origins are explicit;
- post-logout redirect URIs reuse the explicit redirect allowlist.

The adapter owns access, ID and refresh tokens in memory. The application does not
persist them in Web Storage.

On bootstrap the adapter performs `check-sso`. Before a protected API call, the
interceptor asks the adapter to refresh a token that is close to expiry. Refresh
failure clears authentication state and the stale token is not sent.

Login returns to a validated internal route. Logout uses Keycloak's OIDC logout and
returns to `/login`.

## Consequences

- Angular no longer handles operator passwords.
- bearer and refresh tokens are not persisted by application code;
- browser reload authentication is reconstructed from Keycloak SSO state rather
  than from an application-controlled stored token;
- route restoration requires an explicit internal-return-URL validation rule;
- Keycloak must be reachable for login/refresh/logout, while R08 public endpoints
  remain independently accessible;
- the Spring Resource Server authorization model and realm roles do not change;
- R17 can build role-aware route UX on the resulting authenticated role signals
  without changing the authentication protocol again.
