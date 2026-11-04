# Agent runner bindings

The orchestration protocol is deliberately provider-neutral. `tooling/agent-harness/runner.py` binds one immutable task packet to one isolated worktree and one coding-agent CLI. Runtime logs/results go to ignored `.agent-runs/`. The **agent process** never commits or pushes; after successful completion the outer harness may create a local-only checkpoint commit so dependency DAGs can be composed safely.

Every agent launch goes through `agent_harness.execution` with the runner's policy: the task runner (AH5-12b) and,
since AH5-16a, the design loop, Wayfinder and verification-contract authoring. A launch names its model (`--model`
or a configured `DEFAULT_MODELS` entry, never the CLI's own default), is bounded by `--agent-timeout` (default
1800 s, valid 1..86400), receives its prompt on stdin, and runs only on the CLI's own login: API-key variables are
removed, so authenticate through the provider CLI first. Evidence goes to the run directory's `evidence/`; a refused,
failed or withheld launch records `preflight-refused`, `provider-error` or `output-withheld`; a launch that
returns no readable result is a harness error (exit 2), as before. The `design.py`, `wayfinder.py` and
`verification_contract.py generate` invocations in `README.md` predate this and omit `--model`: that file keeps
the bytes committed evidence binds, so add `--model <model-id>` when you run them.
While an agent runs, a SIGTERM or SIGHUP to the launching process (the runner, `design.py`, `wayfinder.py` or
`generate`) cancels the agent before the process exits, including the prototype candidates `design.py` runs in
parallel. SIGKILL cannot be caught: the agent then keeps running in its
own session with no bound, so stop its process group as well.
Claude launches deny the unknown-state marker directory by path; a home directory whose path contains one of
`( ) [ ] { } * ? ! # \` cannot be named in Claude Code's permission rules, so Claude launches refuse to start there.

## Codex

Current non-interactive Codex uses `codex exec`. The runner passes a workspace-write or read-only sandbox, `approval_policy="never"`, disabled web search for deterministic coding tasks, an output JSON schema, and a worktree-specific prompt. Keep approval configuration in explicit CLI/config overrides rather than relying on a user's global Codex profile.

Example after creating/claiming a task:

```bash
python3 tooling/agent-harness/runner.py \
  docs/specs/SHOP-001/packets/T-002.json \
  --provider codex --model "$MODEL" \
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
python3 tooling/agent-harness/runner.py <packet.json> \
  --provider codex --model "$MODEL" \
  --worktree <task-worktree> \
  --verification-sandbox required
```

The sandbox applies **after** a strict executable/argument allowlist. `auto` may degrade to allowlist-only on unsupported hosts, but that degradation is written into result/provenance evidence; `required` does not degrade silently.

Provider telemetry is deliberately asymmetric: use data the provider actually reports. Codex JSONL token usage is recorded when present; Claude JSON usage and provider-reported total cost are recorded when present. The harness does not calculate an authoritative dollar cost for providers that did not report one.

During orchestration, task leases are heartbeated by the outer process. If that process crashes, a subsequent orchestration can recover the task only after the lease has expired, preserving live-worker safety.


## Design-loop runners

`tooling/agent-harness/design.py` launches through the same library execution path as the runner (above), with a dedicated structured output schema. Spec/architecture grills and prototype evaluation are read-only; only `prototype-agent` receives a disposable workspace-write scratch worktree.

The design loop never commits or pushes prototype code and never creates a production dependency checkpoint from it. Candidate patches/logs stay under ignored `.agent-runs/`; only structured findings/evaluations may be copied into durable feature `design/` artifacts.


## Human pause/resume

When a provider returns `needs-human`, the outer orchestrator checkpoints the attempt as needed, marks the task `escalated` and stops the DAG without discarding completed sibling tasks. After a human resolves the ambiguity, use `harness.py human-resolve` to write a durable resolution artifact and grant exactly one explicit resume authorization. The next runner invocation receives that artifact as trusted rework feedback within the existing accepted task contract.

This mechanism is intentionally repo-native. It closes the practical HITL gap identified while evaluating graph orchestration frameworks without introducing a second workflow persistence model. See `runtime-choice.md` for the decision rationale and revisit triggers.
