# API-ACCESS-001 - explicit public API boundary and safe public AI

## Intent

R08 makes the HTTP access boundary fail closed and removes customer-data lookup
capabilities from anonymous AI.

The application deliberately has anonymous storefront APIs and back-office operator
APIs. The previous security configuration ended in `anyRequest().permitAll()`, so a
new `/api/**` endpoint became public unless somebody remembered to add a matcher.

Two existing AI surfaces also crossed the intended trust boundary:

- personalized recommendations accepted any e-mail anonymously and used purchase and
  review history;
- the public support assistant could call `OrderLookupTool` for any supplied order
  number.

There is no customer identity or ownership model in this showcase. R08 therefore does
not invent one.

## Decision

- Public API routes are explicitly allowlisted.
- All unmatched `/api/**` requests are denied.
- Static SPA assets and non-API routes remain available through the final
  `anyRequest().permitAll()`.
- Personalized recommendations require `ORDER_READ`, matching the existing operator
  access model for customer/order data.
- The public support assistant remains anonymous but becomes policy/FAQ only.
- The public support assistant has no order lookup tool and no server-side chat
  memory. `conversationId` remains accepted for API compatibility but is not used as
  a server-side memory key.
- Existing rate limiting, timeout/circuit-breaker fallback and fixed unavailable
  answer remain unchanged.

## Public API allowlist

The following customer-facing API surfaces remain public:

- `/api/cart/**`
- `/api/wishlist/**`
- public `/api/reviews/**`, except `/api/reviews/moderation/**`
- `POST /api/support-assistant/questions`

OpenAPI documentation remains public.

Every other API is either explicitly role-gated or denied by the `/api/**` fallback.

## Role matrix

| Surface | Anonymous | Authenticated without required role | Required role |
|---|---|---|---|
| Cart/Wishlist | allowed | allowed | none |
| Public reviews | allowed | allowed | none |
| Review moderation read | rejected | rejected | `REVIEWS_READ` |
| Review moderation write | rejected | rejected | `REVIEWS_WRITE` |
| Support assistant FAQ | allowed | allowed | none |
| Recommendations | rejected | rejected | `ORDER_READ` |
| Order/catalog/inventory/etc. | rejected | rejected | existing context role |
| Unknown `/api/**` | rejected | rejected | none, explicit matcher required |

## AI data boundary

The anonymous support assistant is allowed to use only the bundled policy knowledge
base. It must not receive a Spring AI tool that can read Orders, Payments, Returns,
Notifications, Shipments or any other customer-specific data.

This is stronger than attempting to detect prompt injection. A prompt cannot escalate
to a capability the model was never given.

The public assistant also does not use shared server-side chat memory. This removes a
cross-user memory-isolation problem in the absence of a customer identity/session
binding. The client may continue sending `conversationId`; it is ignored by this
adapter until a trustworthy identity/session contract exists.

## Personalized recommendations

Recommendations are not anonymous customer self-service in R08. They are a
back-office read because the request key is an arbitrary customer e-mail and the
response is derived from purchase/review history.

`GET /api/recommendations?email=...` therefore requires `ORDER_READ`.

No new realm role is added.

## Acceptance criteria

- AC-001: `WebSecurityConfiguration` contains an explicit `/api/**` deny fallback
  before the final non-API `permitAll`.
- AC-002: an anonymous unknown API request cannot reach MVC as a public endpoint.
- AC-003: an authenticated request to an unknown `/api/**` path is forbidden.
- AC-004: Cart, Wishlist, public Reviews and the support FAQ endpoint remain
  anonymous.
- AC-005: review moderation keeps its existing read/write role split.
- AC-006: recommendations reject anonymous callers.
- AC-007: recommendations reject authenticated callers without `ORDER_READ`.
- AC-008: recommendations pass the security boundary with `ORDER_READ`.
- AC-009: the public support adapter has no `OrderLookupTool` and no dependency on
  `ManageOrderInPort`.
- AC-010: no `@Tool` capability exists in the public support package.
- AC-011: the public support assistant does not install
  `MessageChatMemoryAdvisor`.
- AC-012: missing/blank `conversationId` and arbitrary supplied IDs cannot join
  server-side support chat histories because no support chat history is retained.
- AC-013: disabled/unavailable support AI still returns the existing fixed fallback.
- AC-014: ADR 0017, ADR 0020 and ADR 0036 are explicitly updated/superseded by the
  R08 access decision.
- AC-015: security, AI, web and full repository gates remain green.

## Out of scope

- customer registration/login or per-order customer ownership;
- Authorization Code + PKCE and browser token storage changes from R16;
- role-aware frontend route guards and shared capability maps from R17;
- changing operator authorization for existing order/catalog/inventory/returns/etc.
  APIs;
- changing AI model/provider;
- introducing a new recommendation-specific realm role.
