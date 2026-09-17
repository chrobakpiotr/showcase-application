# INV-CONTENTION-001 - Evaluate high-contention stock reservation strategy

Status: DRAFT DESIGN STUDY
Owner: showcase application maintainer
Risk: high

## Problem / outcome

The current Inventory bounded context deliberately uses optimistic locking plus a bounded business-aware retry loop for stock mutations. Before considering any change for a future high-contention workload, gather reproducible evidence about whether that strategy should remain the default or whether a different persistence technique is materially better.

This is a **design study**, not an approved production feature. No application behavior is authorized to change by this spec alone.

## Actors

- Backend/architecture maintainer evaluating a future scaling decision.

## Scope

### In scope

- Preserve the stock invariant `quantityReserved <= quantityOnHand` under concurrent reservations.
- Compare three candidate persistence strategies in disposable worktrees.
- Use the existing Inventory modules/domain/persistence code and tests as the baseline.
- Record correctness, contention behavior, operational complexity and fit with the current hexagonal architecture.
- Produce a recommendation or explicitly conclude that evidence is insufficient.

### Out of scope

- Shipping any candidate into production.
- Changing the Inventory API, schema, security model or bounded-context ownership.
- Deploying a benchmark environment.
- Claiming production-scale performance from a laptop/local Testcontainers benchmark.

## Domain invariants

- INV-001: A reservation never makes available stock negative.
- INV-002: Inventory stays keyed by SKU and independent from Catalog.
- INV-003: Any retry/recovery strategy must re-evaluate the business rule against current stock state.

## Acceptance criteria

- AC-001: Every candidate is evaluated against the same correctness and contention scenario rather than a different custom workload.
- AC-002: The study reports whether over-reservation occurred, how conflicts/retries were surfaced, and what consistency semantics each candidate relies on.
- AC-003: The study distinguishes local experimental evidence from production claims and records benchmark limitations.
- AC-004: The recommendation explicitly compares correctness, p95/throughput observations when available, implementation complexity, database coupling, failure semantics, operability and architectural fit.
- AC-005: Prototype code remains disposable and no prototype branch/worktree is merged, pushed or treated as production implementation.

## Failure modes and edge cases

- FM-001: Candidate benchmark is not comparable - evaluator must reject the bake-off rather than rank incomparable results.
- FM-002: Local environment is too noisy to distinguish latency - correctness/complexity evidence may still be useful, but performance winner must remain unresolved.
- FM-003: A candidate requires production-only infrastructure - mark the experiment inconclusive instead of reaching remote systems.

## Security / privacy

No secrets, production data, remote infrastructure or privileged operations are permitted.

## Performance / reliability NFRs

The study may record local p50/p95/throughput as directional evidence only. Correctness under contention outranks throughput.

## Assumptions / open questions

- Q-001: At what real contention level would the existing optimistic strategy become operationally unacceptable? This study can expose relative behavior but cannot establish a production threshold without production-like load evidence.
