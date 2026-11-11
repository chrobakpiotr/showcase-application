# API

The HTTP examples below use paths relative to the application context. On the local stack,
prefix them with `http://localhost:9080/home`.

## Authentication and authorization

Angular signs operators in through Keycloak using Authorization Code with PKCE. The browser
adapter keeps tokens in memory and the Angular interceptor attaches bearer tokens to backend
API calls. The stateless Spring Security resource server verifies the JWT signature, expiry,
issuer and audience. The local realm `ecommerce` uses client `ecommerce-app` and an
`ecommerce-app-audience` mapper. See the [realm export](../infra/docker/keycloak/realm-export.json),
[security configuration](../modules/adapters/security/src/main/resources/application-security.yml)
and [ADR 0045](adr/0045-oidc-authorization-code-pkce.md).

| Order API | Required role |
|---|---|
| `GET /api/order/**`, including the recovery timeline and recent analytics | `ORDER_READ` |
| `POST /api/order/analytics/ask` | `ORDER_READ` |
| Other `POST /api/order/**`, including placement and cancellation | `ORDER_WRITE` |

These roles grant back-office access across the order book. Operators act on behalf of customers.
Placement and cancellation log the acting operator. See [ADR 0017](adr/0017-order-api-operator-authorization-model.md).
The backend enforces the role checks independently of the frontend's route guards and write controls.

Anonymous API access is limited to explicitly allowed routes, including the cart, wishlist,
public reviews and policy-only support assistant. Personalized recommendations require
`ORDER_READ`. Unmatched `/api/**` requests are denied. The
[security filter chain](../modules/adapters/security/src/main/java/com/cp/ecommerce/adapter/security/configuration/WebSecurityConfiguration.java)
defines the full access matrix. See [ADR 0044](adr/0044-explicit-api-access-boundary.md) for the rationale.

The [local stack](development.md) starts Keycloak on port 8081. To run just Keycloak, use
`docker compose -f infra/docker/keycloak/docker-compose.yml up -d`. Set
`security.oauth2.issuer-uri` and `security.oauth2.jwk-set-uri` when using a different deployment.

## API documentation

[springdoc-openapi](https://springdoc.org/) exposes both endpoints without authentication while
the application is running.

- [Swagger UI](http://localhost:9080/home/swagger-ui/index.html)
- [Raw OpenAPI spec](http://localhost:9080/home/v3/api-docs)

See [Messaging contracts](contracts.md) for the asynchronous API.

## Idempotent order placement

`POST /api/order` accepts an optional client-generated `Idempotency-Key`. A completed
request with the same key and fingerprint replays the original order number with HTTP 201,
without reserving stock again or incrementing `orders_placed_total`. A different payload or
an in-progress reservation returns HTTP 409 with `urn:problem-type:idempotency-key-conflict`.

The current fingerprint includes customer and line-item details, payment method and coupon.
The database arbitrates concurrent requests through lock stripes. Key completion, order and
stock writes, and the outbox entry share the placement transaction. The
[adapter](../modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/idempotency/IdempotencyKeyAdapter.java)
applies `order.idempotency.stale-after-ms` (default `60000`) to recovery of stale reservations.
The [demo guide](demo-guide.md#retrying-an-order-safely) covers browser retry and the
legacy-fingerprint boundary. Requests without a key have no replay guarantee.

## Error handling

`GlobalExceptionHandler` renders controller errors as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
Problem Details with content type `application/problem+json`. Responses include `type`, `title`,
`status` and `detail`.

- `type` is a `urn:problem-type:<slug>` URI identifying the specific failure category (e.g.
  `urn:problem-type:business-rule-violation`). A `ResponseStatusException` without a dedicated handler
  (e.g. `404` from `GET /api/order/{orderNumber}`) falls back to a slug derived from its HTTP status
  (`urn:problem-type:not-found`), or `urn:problem-type:error` for a non-standard status code.
- Every response carries an `errorId` extension member (a random UUID), which is logged server-side. When the
  request has an active Micrometer trace, the same response also carries its `traceId`; both identifiers are logged,
  so a client-visible failure can be correlated first to the exact log event and then to the distributed trace
  without leaking a stack trace or internal details in the response body itself.
- A unique-key violation from the database (SQLState `23505`) returns 409 `urn:problem-type:data-conflict` with a
  fixed detail; other database integrity failures stay 500. Error logs record only the SQLState of a database
  failure, because database messages echo rejected values. See
  [API-DATA-CONFLICT-001](specs/API-DATA-CONFLICT-001/spec.md).
- Validation failures (`ConstraintViolationException`), domain business rule violations
  (`DomainObjectValidationException`, `BusinessRuleException`), and anything unmapped (`RuntimeException`
  fallback, never leaking the original message) each get their own `type`/`title`/HTTP status - documented
  per-endpoint in Swagger UI via the `@ApiResponse` annotations on `OrderController`.

## API response design (HATEOAS & pagination)

Order responses are [HAL](https://stateless.group/hal-specification.html) documents built with
[Spring HATEOAS](https://spring.io/projects/spring-hateoas) (`EntityModel`, `PagedModel` and `CollectionModel`). Clients can follow the response links.

- **Affordance-driven links**: `GET /api/order/{orderNumber}` always advertises a `self` link, and a `cancel`
  link only while the order is cancellable (`OrderStatus.CONFIRMED`). Clients can use the link to
  show the cancellation action. The backend checks eligibility again on submission.
- **Pagination**: `GET /api/order?page=&size=` (`ListOrdersUseCase`, domain-first `PageQuery`/`PagedResult`
  types rather than binding the domain layer to Spring Data's `Pageable`/`Page`, keeping the hexagonal boundary
  framework-free per [ADR 0001](adr/0001-hexagonal-architecture.md)) returns a `PagedModel` with
  `first`/`prev`/`next`/`last` links, computed from the total element count so a client never has to guess
  whether it has reached the last page.
- `GET /api/order/analytics/recent` uses the simpler `CollectionModel` (a flat list with just a `self` link),
  reflecting that it is a read-only projection with no per-item state machine or pagination cursor to expose
  (see [Order analytics read model](architecture.md#order-analytics-read-model-kafka-consumer)).

## Order recovery timeline

`GET /api/order/{orderNumber}/recovery-timeline?page=0&size=50` requires `ORDER_READ`.
The endpoint projects current durable milestones from placement, payment, fulfillment,
placement dispatch, notifications and shipments. One bounded SQL query serves each page
with stable ordering.

The response excludes claim tokens, error and stack-trace contents, notification
recipient/body/subject and shipment tracking numbers. The Order UI shows the timeline after
successful placement and offers a refresh action. The
[accepted specification](specs/RECOVERY-TIMELINE-001/spec.md) defines the exact contract.
