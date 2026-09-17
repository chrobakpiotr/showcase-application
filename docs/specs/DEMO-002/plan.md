# Implementation plan

1. Retain the controller transaction and pass preparation as an explicit callback
   to the placement use case. The use case fingerprints the original request and
   invokes preparation (coupon + stock) only for a new attempt, before persistence.
   Existing two-argument callers retain an identity preparation overload.
2. Add a small fixed set of database lock rows via additive Liquibase migration.
   Require a writable READ_COMMITTED transaction, lock a stable key hash stripe,
   then issue a separate key lookup before insertion. Missing stripe fails closed. Same-stripe
   requests serialize until the outer transaction commits; unrelated stripes can
   proceed. Require an ambient transaction on reserve/complete. Read-before-insert
   is safe under this lock, avoiding duplicate-insert errors entirely. Stale
   takeover is protected by the same lock and only permitted for an identical
   fingerprint; mismatch is checked before staleness. Hash the version marker
   into the 64-character digest, without a literal storage prefix. No mixed-version writers during rollout.
   Rollback application first with traffic quiesced; keep the additive lock table.
3. Fingerprint version 2 uses length-prefixed nullable fields, epoch milliseconds,
   all submitted customer/item details, payment and coupon. No secrets in logs.
4. Match frontend request URLs using parsed origin and path boundaries. Reject
   malformed URLs without adding a token. Cover protocol-relative URLs too.
5. Keep attempt state in the order component, send immutable payload + UUID via
   service. Serialize submit/retry; distinguish unknown outcomes and explicit new
   order. Add read-only links and query-param inventory prefill.
6. Isolate the E2E SKU per test; provision only that stock. Wait for route landmarks
   and representative rows before layout assertions.

Allowed paths: domain order use case/port/tests; web order controller/tests;
persistence order idempotency classes/tests and changelog; frontend auth, order,
order-list, catalog, inventory components/services/tests and E2E; DEMO-002 docs,
related demo/roadmap docs and pending Returns follow-up. Any further dependency
must be recorded before editing. Independent persistence/security evaluation is
required before completion; this plan is a proposal until their grill completes.

Grill clarifications: unknown outcome includes status 0, 5xx and malformed 2xx.
Once unknown, subsequent 409/401/400 cannot unlock a new attempt; only a valid
order-number replay resolves it. Fingerprinting excludes response-only subtotal,
derived discount and generated customer ID. READ_COMMITTED and stable stripe
mapping are required across all writers; traffic is quiesced for version rollout.
Dependency discovered: the key sequence cycles at 999. The additive migration
must remove cycling and increase its maximum within NUMERIC(13), restarting at 1000 beyond every old legal ID.
CI failure investigation also permits scoped Playwright config/workflow changes.

Integration dependency: application/ecommerce test sources and its Gradle test
dependency on adapter:web are added to exercise the real transactional controller
on H2 and PostgreSQL. No production dependency direction changes.

Encoding clarification from security review: SHA-256 consumes exact big-endian
UTF-16 code units, not a UTF-8 encoder that replaces lone surrogates. This preserves
all accepted Java strings, including escaped lone surrogates from JSON.

Final UI review clarification: an initial 409 with the existing explicit
`urn:problem-type:insufficient-stock` or `urn:problem-type:stock-level-conflict`
is a definitive rollback and permits correction. Unknown/idempotency 409 remains
locked, and no later rejection unlocks an already unknown attempt. Responsive
table routes must wait for their HTTP response and corresponding rendered rows.
