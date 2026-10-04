# Current verification operator reference

This is current repository guidance for the verifier CLI. It supplements the
Agentic SDD overview and accepted feature artifacts; it does not replace,
rewrite, or retroactively approve the SDD-OBS-001 plan or its history. The
external handbook reviewed for F10 is [Agentic SDD Harness Handbook Current
2026-09-30](https://drive.google.com/file/d/1f75msRlG0a8GESoFk3lCmT_m9KDpb8XG/view),
created 2026-09-30 and describing repository snapshot `0695b1d`. That PDF has no
checked-in source in this repository; the old in-repo handbook/PDF/generator
were intentionally removed in `0695b1d` and are not restored here.

Verified on 2026-10-04 against verifier source at repository commit
`3b173ea`. Scope: parser help for all three subcommands and the `run`-without-
`--repo` fail-closed response in the prepared Python 3.12 test container. No
accepted plan was executed and no grant was imported.

## CLI commands

The checked-in parser is `tooling/agent-harness/verify.py`. It has `plan`,
`run`, and `grant-import` subcommands. Use the logical profile ID `showcase`,
which resolves beneath the trusted `tooling/agent-harness/verification-profiles/`
directory. Do not pass a JSON path as `--profile`.

Create an advisory plan with the required repository, profile, base, family and
policy-checkpoint arguments:

```bash
python3 tooling/agent-harness/verify.py plan \
  --repo . \
  --profile showcase \
  --base-sha <full-base-commit-sha> \
  --family-id <stable-family-id> \
  --policy-checkpoint <accepted-policy-checkpoint>
```

`--origin-policy` accepts `integration` (the default) or `task-completion`;
repeat `--task-command` once for each task command when planning task-completion
work. Obtain the base, family and checkpoint from the applicable trusted
workflow/artifacts rather than inventing values. A successful `plan` output is
advisory and grants no execution authority.

Execute only a plan already accepted by the lifecycle authority. Supply the
repository explicitly, including `--repo .` when running from the repository
root, and pass the exact accepted plan identity:

```bash
python3 tooling/agent-harness/verify.py run \
  --mode integration \
  --repo . \
  --plan-id <exact-accepted-verification-plan-id>
```

An exact `--unit-id <unit-id>` may further select a unit in that accepted plan.
`--failure-grant GATE=GRANT` references an already-issued grant for that exact
gate; it does not issue a grant. `run` without `--repo` is
`verification-blocked`, even if a plan ID is supplied. Missing `--plan-id` is
also blocked. Use `python3 tooling/agent-harness/verify.py --help` and the
subcommand `--help` options to confirm syntax for the checked-out revision.

The third parser command imports a signed grant envelope into control state;
that operation mutates control state. Do not use it as a documentation smoke
command; test it only with a disposable fixture and a test envelope. Import does
not create or sign a grant:

```bash
python3 tooling/agent-harness/verify.py grant-import \
  --repo . \
  --grant-file <canonical-signed-grant-envelope.json>
```

## Current implementation limits

The interface and the accepted M5.3 target contract are not the same as a claim
that every target capability is operational. Treat these current findings as
constraints when using or describing verification:

- **F01 — obligation/origin/coalescing gaps:** the accepted target requires
  distinct obligations, trusted execution origins, and authorized coalescing.
  Current planner/authority behavior does not implement that complete contract;
  single-obligation units do not demonstrate coalescing. Do not infer
  independent-origin qualification from command equality, a repeated command,
  or an origin label.
- **F02 — non-cacheable success:** the executor now publishes a validated
  terminal `PASS` for a successful non-cacheable run without reusable GREEN
  evidence, and a later attempt runs it again. This bounded executor behavior
  does not by itself provide accepted-plan admission or complete obligation
  coverage; those lifecycle prerequisites remain separate.
- **F05 — qualification and containment:** production backend qualification is
  still blocked. Current host evidence rejected both probed backends, including
  for descendant containment; Linux active qualification dispatch is
  unsupported. Discovery or a Docker test image is not production qualification,
  and no weaker fallback authorizes execution.
- **F06 — output workspaces:** the implemented source workspace is diagnostic
  materialization only; it grants no launch or `PASS` authority. Newly generated
  outputs remain runtime-only and are checked for links/escapes. Qualified
  writable-root policy, read-only Git metadata for Git-dependent gates, and
  producer/consumer artifact integration remain prerequisites; arbitrary builds
  must not be described as operational through this workspace.
- **M5.3 Design Gate:** the canonical gate is **NOT PASS**. The master-frozen
  corrective protocol is planning material and does not authorize runtime
  mutation or qualify a backend.
- **Human evidence:** the trusted issuer registry is empty, so no issuer can
  currently authorize a signed critical-gate retry grant through it. M5.3
  plan-bound manual coverage is unavailable; do not treat a file, label,
  `--by` value, or physical evidence record as accepted manual coverage.

For the dated implementation evidence behind these limits, consult
[`docs/reviews/S30-implementation-progress-2026-09-30.md`](../reviews/S30-implementation-progress-2026-09-30.md)
and the accepted SDD-OBS-001 spec/plan. Findings describe the recorded state and
must be rechecked against the current code before making later operational
claims.
