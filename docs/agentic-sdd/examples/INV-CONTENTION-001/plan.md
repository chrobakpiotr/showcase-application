# INV-CONTENTION-001 — Draft Technical Study Plan

Status: DRAFT
Spec: `./spec.md`

## Current-system fit

Inventory already uses `StockLevelEntity.@Version`, `SaveStockLevelAdapter.saveAndFlush`, translation to `StockLevelConflictException`, and a bounded retry loop in `ManageStockUseCase`. The study must preserve those semantics as the baseline and must not create a new bounded context.

## Study shape

Use one fixed concurrent reservation scenario against an isolated local database/Testcontainers setup. Each candidate gets a disposable worktree and may change only what is needed to demonstrate the candidate. Candidate code is never promoted automatically.

The predeclared decision criteria are:

1. correctness / no over-reservation,
2. consistency and failure semantics,
3. contention behavior (conflicts/retries/timeouts),
4. p95/throughput observations when the local setup can measure them credibly,
5. implementation complexity,
6. database coupling and portability,
7. observability/operability,
8. fit with current hexagonal boundaries.

## Architecture decision

No ADR change is authorized by this draft. If evidence later justifies changing the current strategy, create a separate accepted feature spec/ADR and normal task DAG.

## Verification strategy

The prototype evaluator must compare candidates using the same fixed criteria and call `needs-human` when evidence is insufficient or incomparable.
