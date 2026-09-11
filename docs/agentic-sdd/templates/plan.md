# <FEATURE-ID> — Technical Plan

Status: DRAFT
Spec: `./spec.md`

## Design preflight inputs/findings

- `design.json`: <present/N/A>
- Spec Grill blockers resolved: <yes/no/N/A>
- Prototype findings used: <design/prototypes/... or N/A>
- Architecture Grill status: <pending/pass/N/A>

## Current-system fit

Identify existing bounded context(s), ports, adapters, ADRs and contracts this change extends. Prefer extension of existing conventions over introducing a new abstraction.

## Proposed design

### Domain

- Aggregates/value objects/invariants affected: ...
- New/changed ports: ...

### Application

- Use case/orchestration changes: ...
- Transaction boundary: ...

### Inbound adapters

- Web/messaging entry points: ...

### Outbound adapters

- Persistence/messaging/AI/AWS/mail/etc.: ...

## Contracts

List exact files to create/change. Do not leave contract decisions implicit.

## Data and consistency

- ...

## Failure strategy

- Timeouts: ...
- Retries: ...
- Duplicate/reordering handling: ...
- Partial failure: ...
- Recovery: ...

## Security analysis

- ...

## Observability

- ...

## Deployment / migration

- ...

## Rollback / forward fix

- ...

## Technical uncertainty / prototype decisions

- Empirical assumptions that could change this plan: ...
- Prototype needed? <yes/no + why>
- If prototyped, selected approach and evidence: ...

## Architecture decision

- Existing ADR(s): ...
- New ADR required? <yes/no + why>

## Verification strategy

Map acceptance criteria to executable evidence:

| Acceptance criterion | Verification | Module/command |
|---|---|---|
| AC-001 | ... | ... |

## Task decomposition rules

- Tasks form a DAG, not an ordered checklist.
- Each task has one primary objective and bounded `allowed_paths`.
- Parallel tasks must not have overlapping write surfaces unless an explicit merge task owns the overlap.
- Evaluator task(s) depend on all implementation tasks they verify.
