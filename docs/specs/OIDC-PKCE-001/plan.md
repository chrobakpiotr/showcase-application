# Plan - OIDC-PKCE-001

1. Record the browser-session and OIDC client decision.
2. Configure Keycloak for standard flow, PKCE S256 and explicit redirects.
3. Add the official Keycloak JavaScript adapter to the Angular application.
4. Initialize OIDC before routing and keep all tokens in adapter memory.
5. Replace password-form login with a redirect to Keycloak.
6. Refresh tokens before protected API requests and clear state on refresh failure.
7. Implement OIDC logout.
8. Update unit and Playwright auth tests.
9. Run frontend and repository quality gates.
