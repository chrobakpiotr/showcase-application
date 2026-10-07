# RUNNER-STRICT-OUTPUT-001 — provider schema projection for task results

## Objective

Keep provider-specific structured-output limitations from changing the
canonical Showcase task-result contract or silently bypassing validation. The
runner derives a strict provider schema from the unchanged canonical schema,
records its provenance, and validates every result against the canonical
schema before accepting it.

## Scope and acceptance criteria

- AC-001: the provider schema is mechanically projected from
  `tooling/agent-harness/schemas/task-result.schema.json`; that canonical file
  remains unchanged.
- AC-002: the projection closes every object and requires every declared
  property. It removes only a constraint explicitly unsupported by that exact
  provider and CLI version.
- AC-003: every structured result is validated against the canonical schema
  after provider output is decoded. Constraints omitted from a projection
  remain enforced; invalid output is a runner contract failure.
- AC-004: tests prove representative canonical-valid output survives each
  supported projection and that output violating each omitted constraint is
  rejected by canonical validation.
- AC-005: the run record stores provider, exact CLI version and projection
  SHA-256. An unknown provider/version or unprojectable schema fails closed
  before provider execution; there is no unstructured-output fallback.
- AC-006: regression tests cover Codex CLI `0.160.0` and Claude Code `2.1.289`.
- AC-007: existing packet integrity, worktree postconditions and provider
  execution behavior remain enforced.
- AC-008: Claude Code can use the already authenticated Claude.ai OAuth session
  while running in restricted mode that ignores untrusted project/user settings;
  no API key or OAuth token is copied into the prompt, task worktree or result.

## Decisions and boundaries

Harness guidance dated 2026-10-07 accepts generated projections, canonical
validation, bidirectional tests and provenance/fail-closed behavior. The
currently evidenced Codex limitation is `additionalProperties: true` and
`uniqueItems`; only `uniqueItems` is dropped for Codex CLI `0.160.0`. Claude
Code `2.1.289` keeps `uniqueItems`: a live structured-output probe accepted
closed objects, all-required fields and uniqueness. Other CLI versions remain
unsupported until a projection profile is tested. A projection profile cannot
silently broaden the canonical contract.

The runner's previous `--bare` flag made the Claude.ai Pro session unusable:
Claude Code's installed CLI help says bare mode requires `ANTHROPIC_API_KEY` or
`apiKeyHelper` and never reads OAuth/keychain credentials. Use `--restricted`
instead, with the runner's explicit settings and tools. A live structured-output
probe on Claude Code 2.1.289 accepted the projected-schema features above and
the same CLI authenticated through the existing OAuth session in restricted
mode. Anthropic documents Claude App Pro/Max OAuth as a supported Claude Code
authentication path.

This feature changes only the local agent runner and its tests. It does not
edit the canonical result schema, change task lifecycle/retry policy, create a
human retry grant for SDD-OBS-001/T-001, or alter any SDD-OBS-001 packet.

## Verification

Run `python3 -m unittest tooling/agent-harness/tests/test_runner.py -v` and
`python3 -m py_compile tooling/agent-harness/runner.py`. The independent
evaluator reruns both checks and inspects the unchanged canonical schema and
run-record bindings.

## Risks

- CLI versions may change schema acceptance. Unknown versions therefore block
  until their exact projection profile has tests.
- The runner currently has hand-written result checks; implementation must
  enforce every constraint present in the canonical task-result schema,
  including nested object fields, patterns, enums and uniqueness, without
  depending on undeclared runtime packages.
- Tight provider schemas may require fields optional in the canonical result.
  Such fields must have explicit, schema-valid empty values; post-provider
  normalization must not invent or conceal invalid provider output.
