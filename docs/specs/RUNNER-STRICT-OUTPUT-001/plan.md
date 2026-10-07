# Plan — RUNNER-STRICT-OUTPUT-001

1. Load and hash the canonical task-result schema. Derive a provider projection
   only for an explicit provider/CLI compatibility profile; fail closed for
   unknown profiles or unsupported schema keywords.
2. Persist the projected schema outside the task worktree, pass it to the
   provider, and bind its digest, provider and exact CLI version into the
   existing run provenance record.
3. Run Claude with `--restricted`, not `--bare`, so the existing supported
   Claude.ai OAuth session is available without loading user/project/local
   settings or ambient MCP configuration. Preserve explicit tool and sandbox
   restrictions; never inject authentication material into task inputs.
4. Validate decoded output against the canonical schema before existing
   semantic, TDD and worktree checks. Preserve custom schemas used by non-task
   design/review commands; do not treat them as the canonical task-result
   profile.
5. Add table-driven projection and canonical-validation tests for both
   provider/version profiles and a regression for Claude restricted auth flags.
   Run the focused suite and compilation check.
6. Have an independent evaluator inspect the result and rerun the declared
   checks. Only then use the Showcase human-resolution protocol to authorize a
   new T-001 attempt if still necessary.

The change is intentionally limited to `runner.py` and its unit tests. It adds
no runtime dependency and does not modify `SDD-OBS-001/tasks.json`, its task
packets, or lifecycle history.

## Task list

- T-001: implement strict provider projections, canonical result validation,
  provenance and regression tests.
- T-900: independently evaluate T-001 against AC-001–AC-008 and rerun its
  declared checks.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| A provider accepts a schema but emits a result that violates omitted constraints | Always validate against the canonical schema after decoding. |
| An unknown CLI version changes structured-output semantics | Require an exact supported profile; record a blocked invocation without fallback. |
| The projection loses a constraint not represented by tests | Projection rejects unknown schema keywords; mutation tests cover each dropped keyword. |
| Provenance describes different schema bytes than sent to the provider | Hash the exact serialized projection and pass those exact bytes via its generated file. |
