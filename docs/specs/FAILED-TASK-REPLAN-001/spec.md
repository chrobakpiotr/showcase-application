# FAILED-TASK-REPLAN-001 — safe packet replan after human resolution

## Objective

Allow an explicitly human-resolved failed task to receive a new immutable
packet revision without rewriting its legacy packet or altering its attempt
and authorization history. The replan is an atomic authority transition; normal
claim/start remains responsible for consuming the next authorized attempt.

## Scope and acceptance criteria

- AC-001: `replan-task` retains its running-task behavior and accepts a failed
  task only when the latest state transition to `failed` is backed by a valid,
  recorded human-resolution audit artifact for that same task and attempt.
- AC-002: the failed-task compare-and-swap binds expected status, attempt count,
  active packet revision, active semantic contract fingerprint, and feature
  generation. Missing or stale bindings fail closed before state mutation.
- AC-003: a successful failed-task replan publishes a new immutable packet
  revision, activates it, and appends its supersession lineage in one atomic
  lifecycle-state replacement. The task remains failed; attempts, retry and
  human authorization history, and the original legacy packet bytes remain
  unchanged. An unpublished orphan revision after a failed state write is
  harmless and cannot become active by itself.
- AC-004: completed tasks, failed tasks without a matching human resolution,
  stale compare-and-swap inputs, and conflicting concurrent replan requests
  are rejected without changing authoritative state. A repeated identical
  committed request is idempotent.
- AC-005: after a successful replan, ordinary claim/start resolves the active
  immutable revision and binds that attempt to its revision and fingerprint;
  replan itself neither claims nor starts an attempt.
- AC-006: tests cover each rejection boundary and the successful transition,
  including byte-identical legacy packet preservation, unchanged attempt and
  authorization history, and claim/start use of the newly active revision.

## Decisions and boundaries

This implements the lifecycle gap confirmed in the 2026-10-07 Harness handoff.
It follows constitution §11's auditable human-resolution gate and the existing
immutable-packet and CAS lifecycle model. The failed path is a separate safe
transition: it does not terminate a running attempt, grant a retry, consume an
authorization, or modify feature/task planning inputs. Replanning an accepted
feature contract remains governed by the existing feature reconciliation
protocol. No direct edits to runtime state or legacy packet files are allowed.

## Verification

Run `python3.13 tooling/agent-harness/tests/test_harness.py` and
`python3.13 -m py_compile tooling/agent-harness/harness.py`. The evaluator must
also inspect the transaction boundary and verify rejected requests preserve the
complete state and packet bytes.

## Risks

- A human-resolution marker alone is insufficient; implementation must validate
  its immutable audit record and binding to the task's current failed attempt.
- Immutable revision publication precedes the atomic state replacement, so a
  crash may leave an unreferenced revision. It must never be treated as active
  without the state pointer and lineage update.
- Feature generation can change independently of task attempts and must be
  included in the request identity and CAS check.
