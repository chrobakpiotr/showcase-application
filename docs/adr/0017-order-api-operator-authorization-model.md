# 0017. Order API authorization model: role-based operator access, not per-customer ownership

## Context

[ADR 0004](0004-jwt-oauth2-resource-server-with-keycloak.md) gates `/api/order/**` purely by realm role
(`ORDER_READ` for `GET`, `ORDER_WRITE` for `POST`) against the whole path. There is no per-order
ownership check anywhere in the call chain: any principal holding `ORDER_READ` can list and view
*every* order in the system (`OrderEntityRepository.findAll(...)`, no `WHERE` scoping), and any
principal holding `ORDER_WRITE` can cancel *any* order regardless of who placed it
(`RequestOrderCancellationUseCase` only checks `order.getStatus() == CONFIRMED`, never who is asking).

Read cold, this looks exactly like a textbook IDOR (Insecure Direct Object Reference) - the kind of
finding a security review should flag. Before "fixing" it, the actual intent behind the current
system needed to be established:

- The Keycloak realm (`etc/docker/keycloak/realm-export.json`) defines exactly **two** demo accounts,
  `order-admin` (`ORDER_READ` + `ORDER_WRITE`) and `order-viewer` (`ORDER_READ` only) - both generic
  staff/technical accounts. There is no per-customer identity anywhere in the realm.
- The Angular frontend has no customer sign-up, customer login, or "my orders" flow - only a single
  staff-facing login screen, which even labels the two accounts by their capability ("full access" /
  "read-only"), not by whose orders they can see.
- `POST /api/order` already lets the authenticated caller submit arbitrary shipping/customer data
  that has no relation to their own identity, and `POST /api/order/{orderNumber}/cancel`'s own
  Javadoc describes it as cancelling "on the customer's behalf" - i.e. the acting principal and the
  order's customer are two different people *by design*.

Taken together, this is the shape of a call-center/back-office (CSR) tool - a small number of staff
accounts act on behalf of any customer - not a direct-to-consumer self-service portal where a
customer would only ever see their own order history.

## Decision

Treat the current role-based (not object-level) authorization as the intended model for this
showcase, and make that explicit rather than leaving it to be mistaken for an oversight:

- `ORDER_READ` / `ORDER_WRITE` are **operator capabilities scoped to the entire order book**, not to
  a specific customer's own orders. No per-order ownership check is added, because none would be
  backed by a real identity model - there is no per-customer login for it to check against.
- Instead of retrofitting an artificial JWT-subject-to-customer ownership check, accountability is
  added at the point where it actually matters: the acting operator's identity (the JWT's
  `preferred_username` claim, resolved via a small new `CurrentOperatorProvider` in `adapter:security`) is
  logged on both sensitive mutating actions, `placeOrder` and `cancelOrder`. Every operator can still
  act on every order, but *who* acted on *which* order is always reconstructable after the fact from
  the structured logs already shipped to Loki (see [Observability](../../README.md#observability)).
- This decision is explicitly scoped to the current deployment shape (a small, trusted set of staff
  accounts). It is **not** a general claim that object-level authorization is unnecessary. If this
  system ever grew a genuine direct-to-consumer self-service channel (customer sign-up/login, a "my
  orders" page), that would need its own principal-to-customer identity mapping and object-level
  ownership checks (e.g. an additional `WHERE customer_id = :authenticatedCustomerId` scoping) layered
  in *alongside*, not instead of, the existing operator roles - most likely as a separate, additional
  role (e.g. `ORDER_READ_OWN`) so both models can coexist.

## Consequences

- A future reviewer (or security audit) no longer has to guess whether "any operator sees every
  order" is a bug - it is documented here as the deliberate consequence of this being a back-office
  tool with no customer self-service surface, matching what the Keycloak realm, the login screen copy
  and the cancellation endpoint's own contract already imply.
- Audit logging gives real accountability value (who did what, when) without the complexity and
  mismatch of bolting on an ownership model the rest of the system doesn't actually support yet.
- If this API were ever exposed directly to end-customers without first adding real per-customer
  scoping, every customer would be able to see and cancel every other customer's orders - a genuine
  IDOR in *that* hypothetical deployment shape. This ADR's decision does not extend to that scenario;
  it would require the additional ownership-scoping role described above before going live.
- Slightly increases log volume (one INFO line per order placement/cancellation) - acceptable given
  logs are already treated as a first-class, queryable signal (structured JSON, shipped to Loki).
