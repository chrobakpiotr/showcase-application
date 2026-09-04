# 0028. Reviews & Ratings bounded context

## Context

With Product Catalog ([ADR 0025](0025-product-catalog-bounded-context.md)), Inventory
([ADR 0026](0026-inventory-bounded-context.md)) and Shopping Cart
([ADR 0027](0027-shopping-cart-bounded-context.md)) in place, the next roadmap item is Reviews &
Ratings: letting customers leave a rating/comment against a SKU, and surfacing an aggregate summary
(average rating, review count) back to the storefront. Unlike Cart, this bounded context needs both a
genuinely public surface (submitting and reading reviews) and a back-office one (moderating them before
they become publicly visible) - the first bounded context in this codebase to need both at once.

## Decision

### SKU-only reference, no dependency on `catalog.Product`

`Review.sku` is a bare string, exactly like `Cart`/`Inventory` before it - the domain object carries no
reference to `catalog.Product`. Resolving a SKU to a real, currently-sellable product (if that check is
ever wanted) is left to the web/composition layer, not the Reviews domain, keeping every bounded context
independently deployable/testable per the precedent set by ADR 0026/0027.

### `REVIEW-<uuid>` string primary key

Mirrors `GenerateCartIdAdapter`/`GenerateSkuAdapter` rather than Catalog's numeric-sequence identifiers.
A review has no natural business key of its own (unlike a SKU) and no ordering requirement that a numeric
sequence would provide, so a generated UUID-based id is the simplest fit.

### Hybrid authorization: public submission/reading, operator-gated moderation

`ReviewController` (`/api/reviews/**`) is `permitAll()` - submitting a review and reading approved
reviews/summary is exactly the kind of anonymous, customer-facing action Cart already established as a
precedent (ADR 0027). `ReviewModerationController` (`/api/reviews/moderation/**`), however, is gated
behind new `REVIEWS_READ`/`REVIEWS_WRITE` Keycloak roles, following the operator-role model used by
Order/Catalog/Inventory. Both live under the same `/api/reviews` path prefix, so
`WebSecurityConfiguration` declares the more specific `/api/reviews/moderation/**` matcher *before* the
broader `/api/reviews/**` `permitAll()` matcher - Spring Security evaluates matchers in declaration
order and stops at the first match, the same technique already used for
`ANALYTICS_ASK_API_PATH_MATCHER`. This is the first bounded context to need both an open and a gated
surface at once, rather than being fully one or the other.

### Aggregate summary computed via query, not in-memory averaging

`ComputeReviewSummaryOutPort` is backed by a derived `countBySkuAndStatus` plus an explicit
`@Query("SELECT AVG(r.rating) ...")` for the average, mirroring
`OrderAnalyticsProjectionEntityRepository`'s aggregate-query precedent. Loading every approved review for
a SKU into memory just to average its ratings would not scale and has no benefit over letting the
database do it.

### Idempotent, unconditional moderation actions - no optimistic locking, no new conflict exception

Unlike `SaveCartAdapter`/`SaveStockLevelAdapter`, `SaveReviewAdapter` has no `@Version` column and uses a
plain `save()`. Moderation (`approveReview`/`rejectReview`) is a single back-office operator action, one
at a time, on a single review - there is no realistic multi-writer contention scenario to protect against
the way there is for stock or checkout, so no `CartConflictException`-style translation is needed here.
Both actions are also idempotent: re-approving an already-approved review, or moderating a review id that
no longer exists, is not treated as an error - the former is a no-op overwrite, the latter returns `null`
and the controller responds `404`, the same missing-resource convention already used by Cart.

### No pagination on list endpoints

`GET /api/reviews` (approved reviews for a SKU) and
`GET /api/reviews/moderation/pending` both return an unbounded list. This is a deliberate scope-limiting
decision for a showcase application; a production system would paginate both, but doing so here would add
API surface without demonstrating a new architectural pattern.

### Manual controller-level validation, not `@Valid`

`ReviewController` validates its request body (`requireNonBlank`, `requireValidRating`) with explicit
checks that throw `ResponseStatusException(BAD_REQUEST, ...)`, the same style already used by
`CartController.requireSku`/`requirePositiveQuantity`, rather than introducing Bean Validation
annotations on the web-layer resource for the first time in this bounded context.

## Consequences

- New Liquibase changeset adds the `REVIEW` table plus two supporting indexes (by `sku`+`status`, by
  `status`+`created_date`); no existing table is touched.
- Two new Keycloak roles, `REVIEWS_READ`/`REVIEWS_WRITE`, are added for the moderation endpoints only -
  the public submit/list/summary endpoints require no role at all.
- `WebSecurityConfiguration`'s "more specific matcher declared first" technique is now established with
  two real precedents (Analytics, Reviews); any future bounded context needing a mixed
  public/operator-gated API under a shared path prefix should follow the same approach rather than
  splitting into two unrelated path prefixes purely to simplify the security rule ordering.
- Purchase verification ("did this customer actually buy this SKU before reviewing it") is deliberately
  out of scope, consistent with there being no persisted customer-account concept anywhere else in this
  application (see ADR 0027's context section) - it would require solving that problem first.
