# 0004. JWT / OAuth2 Resource Server backed by Keycloak

## Context

The order API (`/api/order/**`) needed authentication and authorization, while the Angular
frontend, Swagger UI and actuator health/info/metrics endpoints should remain publicly reachable so
the showcase can be explored immediately without a login wall in front of the demo itself. The
solution also needed to be realistic and portable - not a toy/in-memory auth scheme - since
demonstrating standard, production-grade security practices was one of the explicit goals of
extending this project.

## Decision

Use Spring Security's OAuth2 Resource Server support, validating JWT bearer tokens issued by an
external identity provider (Keycloak), rather than implementing custom session/cookie-based auth
or a hand-rolled JWT issuer inside the application itself:

- `ORDER_READ` / `ORDER_WRITE` realm roles gate `GET`/`POST` on `/api/order/**` - a wildcard match, so
  every endpoint added later under this path (paginated listing, cancellation, the analytics read
  model) is covered automatically without touching this security configuration again.
- Everything else (frontend static assets, Swagger UI, actuator endpoints) is permitted without
  authentication.
- A ready-to-use local Keycloak instance (realm `ecommerce`, client `ecommerce-app`, two demo
  users) is provided via `etc/docker/keycloak/docker-compose.yml`, so the whole auth flow can be
  exercised end-to-end locally without a manual Keycloak setup.
- The resource server only validates tokens (issuer/JWK-set URIs); it never issues them - Keycloak
  remains the single source of truth for identity, matching how this would work in a real
  microservice/BFF setup.

## Consequences

- The application itself carries no password storage, token issuance, or session state - it only
  needs to trust Keycloak's signing keys, keeping the resource server stateless and horizontally
  scalable.
- Running the app requires Keycloak to be reachable (locally, via the provided compose file) for
  any secured endpoint to work - acceptable since public endpoints (frontend, docs, health) still
  work without it, and the compose file makes spinning it up trivial.
- Demonstrates a realistic OAuth2/OIDC integration pattern (realm roles → Spring Security
  authorities) that maps directly to how many real-world systems delegate auth to Keycloak/Auth0/
  Okta, rather than a bespoke or oversimplified scheme.
