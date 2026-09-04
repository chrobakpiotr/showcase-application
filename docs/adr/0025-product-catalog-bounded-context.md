# 0025. Product Catalog bounded context

## Context

Up to this point the domain modelled a single bounded context: order placement/cancellation, with an
`Order` that carried only `remarks` and a `Customer` - no notion of *what* was actually being ordered.
Extending the application towards a "mature ecommerce" shape requires a Product Catalog: something to
browse, filter and eventually reference from an order line item.

This ADR covers the first of a roadmap of new bounded contexts (Product Catalog → Inventory/Shopping
Cart → Order line items → Payment → Reviews & Ratings), each added incrementally behind the existing
hexagonal architecture boundaries (`domain` / `adapter:persistence` / `adapter:web`) and the same
100%-line-coverage, ArchUnit-enforced conventions as the `order` context.

## Decision

### Two domain objects, deliberately asymmetric

- **`Category`**: `id`, `name`, `slug`. Carries a real persistence `id` because categories are looked
  up and referenced by both slug (external, stable) and id (internal, cheap FK).
- **`Product`**: `sku`, `name`, `description`, `category` (a `Category`), `unitPrice`, `imageUrl`,
  `active`, `created`. **Has no `id` field at all** - `sku` is its business key, mirroring
  `Order.orderNumber`. This is intentional, not an oversight: the same "business key as the only
  externally visible identity" convention already used for `Order` is reused here for consistency,
  and it avoids ever leaking a surrogate DB id through the API.

  Consequence: `SaveProductAdapter` cannot just `mapToEntity(product).save()` - since the mapped
  entity always has a `null` id, that would INSERT a duplicate row on every update and fail the SKU
  unique constraint. The adapter looks the existing `ProductEntity` up by SKU first and copies its id
  onto the freshly-mapped entity before saving. `Category` has no such problem since it carries `id`
  directly.

### Category resolution is a two-phase, web-mapper/use-case split

A `ProductResource` request payload carries a `categorySlug`, not a nested `Category`. `ProductWebMapper`
builds a category-less "draft" `Product` from the resource and exposes `extractCategorySlug()`
separately; the use case (`ManageProductUseCase`) resolves the slug to a real `Category` via
`FindCategoryOutPort` and only then finishes constructing a fully-valid `Product`. This works because
`ValidDomainObject.validate()` (invoked by the Lombok builder) only *records* violations -
`assertValidationsEmpty()` must be called explicitly to throw - so an intermediate, not-yet-valid draft
object can exist as a controlled step without corrupting the domain's fail-fast validation guarantees
anywhere else.

Updating a product intentionally excludes changing its category through this endpoint (`PUT
/api/catalog/products/{sku}` only touches the mutable commercial attributes) - moving a product between
categories is a distinct, rarer operation deliberately left out of the v1 API surface rather than
smuggled into a generic update.

### Bounded-context-local pagination types, not shared with `order`

`PagedResult`/`ProductPageQuery` are defined locally inside `domain/catalog`, deliberately **not**
reusing the existing `order` module's paging types even though they are structurally similar. Each
bounded context owns its own contracts; a shared "common paging" abstraction would create a coupling
seam between contexts that otherwise have no reason to know about each other, for a few lines of
duplication that cost nothing to maintain.

### SKU generation: pure UUID, no DB sequence

`GenerateSkuAdapter` produces `SKU-<uuid>` with no database round-trip and no sequence - SKUs need to
be unique and stable, not sequential or human-guessable, so a UUID is sufficient and keeps product
creation independent of any persistence detail.

### Flat category model

Categories are flat (no parent/child hierarchy) for this iteration. A real catalog eventually wants
nested categories, but that is a genuine scope increase (tree traversal, breadcrumb resolution, moving
subtrees) better deferred until a concrete need for it shows up rather than spent upfront.

### Authorization: reuse the existing operator model, don't invent a new one

Per [ADR 0017](0017-order-api-operator-authorization-model.md), the realm models a small number of
back-office/CSR-style operator accounts, not per-customer identities. `CATALOG_READ`/`CATALOG_WRITE`
mirror `ORDER_READ`/`ORDER_WRITE` exactly (`GET` → READ, `POST`/`PUT` → WRITE) rather than introducing a
different authorization shape just for this context - `order-admin` gets both, `order-viewer` gets
`CATALOG_READ` alongside its existing `ORDER_READ`.

## Consequences

- New Liquibase changesets (`CATEGORY`, `PRODUCT` tables + indexes + FK + sequences) are additive only;
  no existing table is touched.
- The Angular frontend gets a new lazy-loaded `/catalog` route (browsing only - creating
  categories/products is deliberately left as a back-office/API-only capability for now, consistent
  with there being no "admin UI" anywhere else in the frontend either) gated behind `CATALOG_READ`,
  mirroring the `/analytics` route's role-gated nav-link pattern.
- Because `Product` has no `id`, any future bounded context that needs to reference "the product" (e.g.
  order line items, shopping cart entries) will naturally reference it by `sku`, not a surrogate key -
  this ADR's asymmetric-id decision therefore also constrains the shape of every future FK to `PRODUCT`.
- The next roadmap items (Inventory, Shopping Cart) will each get their own ADR when their own
  bounded-context-specific decisions arise.
