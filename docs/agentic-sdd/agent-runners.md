# Agent runner bindings

The orchestration protocol is deliberately provider-neutral. `etc/agent-harness/runner.py` binds one immutable task packet to one isolated worktree and one coding-agent CLI. Runtime logs/results go to ignored `.agent-runs/`. The **agent process** never commits or pushes; after successful completion the outer harness may create a local-only checkpoint commit so dependency DAGs can be composed safely.

## Codex

Current non-interactive Codex uses `codex exec`. The runner passes a workspace-write or read-only sandbox, `approval_policy="never"`, disabled web search for deterministic coding tasks, an output JSON schema, and a worktree-specific prompt. Keep approval configuration in explicit CLI/config overrides rather than relying on a user's global Codex profile.

Example after creating/claiming a task:

```bash
python3 etc/agent-harness/runner.py \
  docs/specs/SHOP-001/packets/T-002.json \
  --provider codex \
  --worktree ../showcase-application-agent-worktrees/SHOP-001/T-002 \
  --reasoning medium
```

## Claude Code

Claude Code's non-interactive interface is `claude -p`. The runner requests JSON structured output and enforces turn/budget limits. Project bindings in `.claude/agents/` are intentionally thin; the canonical role instructions stay in `docs/agentic-sdd/agents/`.

For multi-agent work, prefer **independent worktree sessions controlled by this DAG** for builder/evaluator separation. Claude Code subagents remain useful for narrow side analyses. Do not depend on experimental free-form agent-team coordination for the correctness boundary of a feature.

## Cross-model evaluation

For high-risk changes, a strong setup is:

```text
architect:  high-reasoning model
builder A:  coding model in worktree A
builder B:  coding model in worktree B
evaluator:  fresh context, optionally different provider/model
specialist: only when triggered by risk_tags
integration: deterministic gates first, model judgment second
```

A different provider for the evaluator is optional. **Fresh context and independent acceptance evidence are mandatory; provider diversity is not.**

## Execution hardening

The runner records structured provenance for every invocation and independently reruns task `verification` commands after a builder reports success. Verification can be required to run inside a strong local OS sandbox:

```bash
python3 etc/agent-harness/runner.py <packet.json> \
  --provider codex \
  --worktree <task-worktree> \
  --verification-sandbox required
```

The sandbox applies **after** a strict executable/argument allowlist. `auto` may degrade to allowlist-only on unsupported hosts, but that degradation is written into result/provenance evidence; `required` does not degrade silently.

Provider telemetry is deliberately asymmetric: use data the provider actually reports. Codex JSONL token usage is recorded when present; Claude JSON usage and provider-reported total cost are recorded when present. The harness does not calculate an authoritative dollar cost for providers that did not report one.

During orchestration, task leases are heartbeated by the outer process. If that process crashes, a subsequent orchestration can recover the task only after the lease has expired, preserving live-worker safety.


## Design-loop runners

`etc/agent-harness/design.py` reuses the same bounded local Codex/Claude CLI command builders but supplies a dedicated structured output schema. Spec/architecture grills and prototype evaluation are read-only; only `prototype-agent` receives a disposable workspace-write scratch worktree.

The design loop never commits or pushes prototype code and never creates a production dependency checkpoint from it. Candidate patches/logs stay under ignored `.agent-runs/`; only structured findings/evaluations may be copied into durable feature `design/` artifacts.


## Human pause/resume

When a provider returns `needs-human`, the outer orchestrator checkpoints the attempt as needed, marks the task `escalated` and stops the DAG without discarding completed sibling tasks. After a human resolves the ambiguity, use `harness.py human-resolve` to write a durable resolution artifact and grant exactly one explicit resume authorization. The next runner invocation receives that artifact as trusted rework feedback within the existing accepted task contract.

This mechanism is intentionally repo-native. It closes the practical HITL gap identified while evaluating graph orchestration frameworks without introducing a second workflow persistence model. See `runtime-choice.md` for the decision rationale and revisit triggers.
