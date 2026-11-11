# Project improvement roadmap

Current work is tracked in [Backlog](backlog.md). The table below separates implementation
from qualification and external publication. Earlier source-bound records retain their original status.

| Work | Current recorded state | Next step |
|---|---|---|
| [Architecture follow-up](specs/SHOWCASE-FOLLOWUP-001/spec.md) | Implemented. Bounded verification PASS. Hosted CI at `0b18f3d` passed Playwright, the critical PostgreSQL and RabbitMQ gates and Agentic SDD ([record](specs/SHOWCASE-FOLLOWUP-001/evidence/hosted-ci-2026-11-11.md)) | Real-stack k6 smoke passed after [ORDER-CUSTOMER-EMAIL-001](specs/ORDER-CUSTOMER-EMAIL-001/spec.md) ([record](specs/ORDER-CUSTOMER-EMAIL-001/evidence/k6-smoke-2026-11-11.md)). Hosted CI passed at `84066c0` ([CI repair](specs/ORDER-CUSTOMER-EMAIL-001/evidence/ci-repair-2026-11-11.md)) |
| SDD-OBS-001 execution slice | Completed in the [2026-10-17 record](reviews/showcase-master-review-progress-2026-10-05.md#verification-v2-completion---2026-10-17) | Apply current accepted-plan and qualification requirements to future execution |
| Canonical M5.3 design gate | NOT PASS | Follow the [verification handbook](agentic-sdd/handbook.md#status-and-safety-boundary) |
| S30-06 | DEFERRED, design gate OPEN, NOT QUALIFIED | Re-entry requires the target/control mechanism or an owner-approved scope revision in the [deferral decision](specs/S30-AMQP-POISON-001/evidence/s30-06-deferral-decision-2026-10-18.md) |
| S05-07 external handbook | Repository delta ready, external application unverified | The handbook owner applies and verifies the [source-linked delta](reviews/external-handbook-delta-2026-10-06.md) |
| Provider profiles and live execution | Version evidence and exact backend qualification required | Follow [Backlog](backlog.md) and the [handover](handover/AGENT_HANDOVER.md) |

## Implemented in the hardening patches

- Deprecated-stack migration: Angular legacy animations removed, Jackson 3 adopted end-to-end (including Redis serialization), Boot 4.1/Gradle deprecated APIs migrated, CodeQL moved to v4, and Promtail replaced by Grafana Alloy.
- Repository cleanup removed the dependency-only backend composition module, folded application assembly into the boot app, removed redundant runtime configuration-processor wiring, made Agentic SDD tooling paths relocation-safe, and standardized container config mounts under `/config`.
- README and `docs/**/*.md` now have an offline local-link/anchor checker with regression tests, wired into pre-push verification and a dedicated CI documentation gate.
- Gradle quality scripts use consistent tool-specific folders under `tooling/`, including `tooling/quality/jacoco/jacoco.gradle` and `tooling/quality/spotbugs/spotbugs.gradle`.
- Frontend RFC 9457 rendering is centralized in a tested `ProblemDetailsAdapter`; order retry semantics still classify deterministic rejection separately from unknown outcomes.
- Inventory blocks duplicate/overlapping requests, clears stale lookup results and
  dispatches only the selected stock operation.
- Demo SKU suggestions, accessible pending status and wrapping action buttons.
- Playwright retains traces and screenshots on failure even with zero retries.
- E2E TypeScript configuration uses Node16 resolution and explicit Node types.
- Main README contains quick start, license and navigation. The [documentation map](README.md)
  routes architecture, API, domain, AI, deployment, operations and testing material under `docs/`.
- DEMO-002 makes order idempotency transactional, fingerprints all submitted
  request fields, hardens the browser token boundary and keeps one immutable
  retry attempt while the component remains mounted.
- DEMO-003 persists only an unresolved retry snapshot in tab-scoped storage with
  versioning/expiry, so an unknown order outcome can survive reload and replay
  the same Idempotency-Key.
- Cart and Wishlist share one cart-session boundary; transient cart-load failures
  preserve the known cart instead of silently replacing it.
- Shipments and Reviews use latest-request-wins reads and explicit in-flight
  mutation guards for the stale-read/double-action seams found in review.
- Playwright uses a disposable E2E-only Compose project locally and in CI, with
  volumes removed on teardown and observability UI/log-shipping services omitted.
- Agentic SDD dogfoods DEMO-003 and runs a checked-in adversarial eval suite for
  content mutation, secret isolation and reopen safety regressions.

## Verification boundaries

Angular unit tests prove storage expiry/recovery, request ordering and duplicate
action guards. The Playwright recovery scenario additionally proves the real
server-accepted/browser-unknown order seam against the containerized stack.

Full backend gates remain required before integration of backend changes. DEMO-003
does not change production backend behavior, but its dedicated E2E stack still
boots the real application image with Postgres, RabbitMQ, Kafka, Redis, Keycloak
and Tempo. Frontend coverage thresholds remain unchanged at 100%.
