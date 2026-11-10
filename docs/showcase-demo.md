# 10-minute showcase demo

1. Start the [local Docker stack](development.md#full-containerized-stack) and open `/home`.
2. Authenticate through Keycloak Authorization Code + PKCE.
3. Place an order and repeat the same request with the same idempotency key.
4. Inspect payment and the [recovery timeline](api.md#order-recovery-timeline).
5. Demonstrate a recoverable downstream failure and the durable retry state.
6. Cancel an eligible order and verify reservation-aware release/refund.
7. Create and moderate a partial return and verify the partial refund ledger.
8. Create a shipment only after captured payment; dispatch consumes the order-owned reservation once.
9. Log in as the read-only operator and confirm write capabilities are not suggested by the UI and remain blocked by the backend.
10. Review [recovery runbooks](operations.md#recovery-runbooks) and [CI security gates](testing.md#supply-chain-hardening).

## Boundaries

This repository is production-shaped, not a production commerce platform. Payment and several external integrations are demo adapters. Durable workflows use at-least-once side effects and rely on stable identities for idempotency. Backend authorization remains authoritative regardless of frontend role visibility.
