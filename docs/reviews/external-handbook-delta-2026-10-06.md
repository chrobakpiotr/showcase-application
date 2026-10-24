# External handbook delta — Showcase master review — updated 2026-10-18

Prepared for the designated owner of the separate handbook. This is a
source-bound handoff from the Showcase repository; it does not update that
handbook or qualify production. Apply only after checking the linked commits
and fresh evidence against the intended handbook revision.

## Changes and evidence to carry over

- **Shipment response-loss E2E:** commit `69beca0` snapshots the committed
  response with `route.fetch()` before fulfilling the route and allowing page
  navigation. The focused [`shipment-response-loss.spec.ts`](../../apps/ecommerce/frontend/e2e/shipment-response-loss.spec.ts)
  passed all six executions across three repeats with retries disabled on the
  disposable Compose stack. The main CI Playwright job also passed on pushed
  `a6fac3c33376` in [run 37512052634](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052634).
- **RabbitMQ critical-result verifier:** commit `baaaef7` rejects duplicate
  normalized testcase identities instead of allowing a set comparison to hide
  duplicate executions. The tooling suite passed 50 tests; implementation and
  regression tests are in [`verify_critical_rabbitmq_results.py`](../../tooling/scripts/verify_critical_rabbitmq_results.py)
  and [`test_verify_critical_rabbitmq_results.py`](../../tooling/scripts/tests/test_verify_critical_rabbitmq_results.py).
- **Log privacy:** `f7526d3` narrows order-summary logging; `52e1837` covers
  dispatch/mock logging with allowlists and bounded path inventory. The
  focused inventory and capture evidence is in
  [`S05-03b-log-privacy-inventory-2026-10-05.md`](S05-03b-log-privacy-inventory-2026-10-05.md).
  Do not imply that ordinary logs replace the separate audit trail.
- **S30-06 reference scope:** accepted D1–D5 and the narrow Candidate C
  contract delta are recorded in the current `spec.md` and `plan.md`.
  Master has deferred implementation: realization is **DEFERRED**,
  qualification **NOT QUALIFIED**, the design gate **OPEN**, and the consumer
  disabled. This changes scheduling only; all accepted requirements remain in
  force. P-007 closed as preflight analysis **BLOCKED before runtime** because
  no existing restore wrapper or stale-episode start guard was found, while
  this shell identity can access Docker directly. Process termination,
  old-episode restart refusal, Rabbit drain/redelivery, stale-generation CAS,
  and credential reuse remain separate **NOT RUN** results. No plugin was
  selected and no controller/service was built. Resume only when a concrete
  target/control mechanism exists or the owner accepts a scoped REF-Q revision;
  do not initiate further searches or experiments before that. See the
  [decision record](../specs/S30-AMQP-POISON-001/evidence/s30-06-deferral-decision-2026-10-18.md)
  and [P-007 report](../specs/S30-AMQP-POISON-001/design/prototypes/P-007/runs/2026-10-18-preflight-blocked/run-report.md).
  REF-Q and PROD-Q remain separate; S30-06 remains OPEN. The design gate is
  still OPEN because its hash-bound verification contract, independent grills,
  and exact-target evidence have not passed. Fresh read-only architecture and
  scope reviews on 2026-10-10 confirm Candidate C is a direction, not a closed
  design: restore freshness/anti-rollback, per-instance identity and exact
  Rabbit connection fencing, PG/Redis cross-store recovery, and permit/start
  linearization still need decisions and falsification evidence. The original
  design-only P-005 probe observed RabbitMQ 4.1.8 connection close and immediate
  reconnect through administrator-authenticated DELETEs (`p005admin`); the
  fencer identity itself did not close connections. It also observed live
  old-handler behavior, stale synthetic PostgreSQL generation rejection, and
  ACK failure/redelivery. The timeout injection showed transport timeout only:
  `admission_open` was hard-set false before the request, so no admission
  reaction was tested. After-close drain was not tested across all three APIs;
  only `/api/connections` was re-read. The separately tested administrator-tagged
  fencer could list users even though queue payload GET was denied; this is
  broader than the accepted least-privilege boundary, so no fencer candidate
  passes and the target remains NOT QUALIFIED. The original successful
  reconnect sample recorded zero channels/consumers for both connections, so
  channel identity across reconnect was unverified there. A corrected one-shot follow-up opened a channel and broker-generated consumer
  on both same-principal incarnations; its detailed report is under the P-005
  run directory. The [corrected follow-up report](../specs/S30-AMQP-POISON-001/design/prototypes/P-005/runs/20261017T202943Z-9f3564a897-reconnect-followup/followup-report.md) is one immediate single-node observation only; it does not establish a settled interval. The workload-to-Rabbit-principal mapping was a synthetic
  fixture, not Keycloak provisioning. Harness review also requires principal
  blocking before close, broker-generated consumer identity, settled agreement
  among the three drain APIs or an event source, and a concurrent conditional-
  write/fence test. See the P-005 errata for exact boundaries; the bounded
  follow-up does not test principal blocking or close the read/delete TOCTOU gap.
  See the
  [P-005 research report](../specs/S30-AMQP-POISON-001/design/prototypes/P-005/README.md)
  and its bound [run result](../specs/S30-AMQP-POISON-001/design/prototypes/P-005/runs/2026-10-17/report.json).
  The 45–48 ms host-record observation remains a visibility result only;
  durability, integrity, path permissions and anti-rollback have separate
  unproven evidence rows in the [owner checklist](../specs/S30-AMQP-POISON-001/evidence/owner-encryption-checklist-2026-10-17.md).
  Direct/provider restore, wrapper bypass and full-host rollback remain outside
  the proposed REF-Q guarantee. Remaining
  06b retry, parking, invalid-identity, retention and ordering rules also stay
  open. The review synthesis is in
  [`design-readiness-followup-2026-10-10.md`](../specs/S30-AMQP-POISON-001/evidence/design-readiness-followup-2026-10-10.md).
  The point-by-point mapping from existing prototypes to their unresolved
  claims is in the [design evidence coverage matrix](../specs/S30-AMQP-POISON-001/evidence/design-evidence-coverage-matrix-2026-10-17.md).
  Candidate C is the accepted REF-Q design direction, not implementation or
  qualification evidence. No AMQP consumer is enabled and no production
  readiness is claimed. See the
  [specification](../specs/S30-AMQP-POISON-001/spec.md),
  [plan](../specs/S30-AMQP-POISON-001/plan.md), and
  [`candidate-c-spec-amendment-accepted-2026-10-07.md`](../specs/S30-AMQP-POISON-001/evidence/candidate-c-spec-amendment-accepted-2026-10-07.md).
- **PostgreSQL measurement fixture:** `0c1f4e2` preserves deterministic
  synthetic fixtures and all 60 JSON plans. The evidence is described in
  [`S05-05a-postgres-query-measurement-2026-10-05.md`](S05-05a-postgres-query-measurement-2026-10-05.md).
  These are warm-run query-shape observations, not production traffic or an
  accepted SLO. The later S05-05b synthetic profile and three-run comparison
  are linked below.
- **Parked-dispatch UI:** `513c7df` adds a read-only page on the existing
  ORDER_READ endpoint, with bounded safe reason labels, age/attempt details,
  refresh, empty/error/loading states, and no redrive control. The focused
  Angular suite passed 33 tests, and the browser check covered anonymous
  denial, ORDER_READ access, absence of mutation controls, and 320/768/1024/1440
  pixel viewports. This does not imply operator redrive support.
- **Dependency risk, historical:** `a0f12b9` added expiry validation and
  `a675836` temporarily recorded the exact DOMPurify
  `GHSA-6688-9rhm-gjv2` accepted risk through 2026-11-03 without changing the
  CVSS threshold. That entry was later removed after `deddce1` upgraded the
  Swagger UI WebJar and its vulnerable transitive dependency. The old OWASP
  result on `a6fac3c33376` in [run 37512052634](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052634)
  is historical; see the current scan below.
- **Harness consumer pin:** `4fd9abe` adds
  `tooling/agent-harness/requirements.txt` to the protocol fingerprint input;
  a focused regression test proves a pin change changes the digest. This
  Showcase-side consumer change does not alter ownership of the shared
  execution contract, which remains with Harness.
- **04b reference-target qualification:** the selected report under
  [`evidence/selected/`](../specs/AH5-04B-QUAL-001/evidence/selected/) passes
  all 26 Q01–Q16/B1–B10 checks and the pinned Harness v0.5.0 checker for the
  exact Docker Desktop Linux guest tuple: Docker Engine 29.8.2, kernel
  7.0.14-linuxkit, workload image digest
  `sha256:9d72651cf7018c1f6a1dd6fd02bd68286631c33620bc0f37b0675b21aab915d5`,
  job `local-20261008T205033Z-7939417fefb5`, qualification digest
  `sha256:f7cdab99c3fb6b18bb25e71c99199235007553ec4c140d62b305cccff29a2600`.
  The independent T-900 review is bound to report subject
  `sha256:64e41e360c322bd8fbd216d22b77857edd5870bfd3c6734150c408536814170e`.
  The capability is qualified for this target only; `launch_ready=false` and
  refusal `BACKEND_UNAVAILABLE` remain. The GitHub runner is not qualified,
  and this evidence does not claim production readiness or authorize payload
  execution.

## Current implementation and CI update — 2026-10-17

- **SDD-OBS-001 verification-v2 slice:** the current Showcase lifecycle reports
  T-001 through T-009, T-900, and T-990 completed. T-900's corrected repair
  evidence passed full discovery (922 tests) in the pinned evaluator image;
  Harness accepted the T-900 and T-007 repairs. T-990 was re-gated against
  those repaired inputs and its closure record reports 144/144 criteria
  passed. See the [T-990 closure repair record](../specs/SDD-OBS-001/evidence/integration/T-990/repair-2026-10-14/closure-repair.json),
  [T-900 repair report](../specs/SDD-OBS-001/evidence/T-900-repair-2026-10-13/report.json),
  and [integration report](../specs/SDD-OBS-001/evidence/integration/T-990/integration-report.md).
  This is an implementation handoff, not a production qualification or closure
  of the separate S30-06 design gate.
- **Current main verification:** pushed SHA
  `9455e8a36c1694998b0a521fe48099f71c45585c` passed [CI](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281347),
  [Agentic SDD CI](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281330),
  [CodeQL](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281331),
  and [OpenSSF Scorecard](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281343).
  The push event skipped Dependency Review. CI includes backend quality/tests,
  PostgreSQL and RabbitMQ evidence checks, Playwright, frontend checks, OWASP
  DependencyCheck, SBOM, Trivy, PIT, infrastructure validation, repository
  guards, and documentation links.
- **OWASP dependency correction:** `deddce1` upgrades the managed Swagger UI
  WebJar to `5.33.2` and removes the two accepted-risk suppressions for the
  bundled vulnerable DOMPurify version. `9455e8a` refreshes the registered
  Gradle input digest so verification retry evidence binds to the changed
  committed `build.gradle`. OWASP passes on current `main`; do not carry the
  prior accepted-risk entry forward as an open risk.
- **Remaining master-plan limits:** S30-06 remains OPEN with its design gate
  open; REF-Q is distinct from PROD-Q and no production qualification is
  claimed. S05-05b now has an explicitly synthetic experiment profile and
  comparison runner; results do not define a production workload or SLO. S05-07
  remains partial until the designated owner applies this source-linked delta
  to the separate handbook.

## New source-bound handoff — 2026-10-17

The following repository deltas are ready for the separate handbook owner.
The git source is currently the unmodified baseline
`49ab97f580ad21c67e9378e15448aca9fe365c45`; the related proposal/profile and
runner changes are local and uncommitted. Apply them only after receiving the
resulting commit SHA and checking the listed evidence hashes.

| Handbook section to update | Showcase source | Required status/limitation |
|---|---|---|
| S30-06 reference target; restore/fencing/drain; REF-Q versus PROD-Q | [`reference-target-proposal-2026-10-17.md`](../specs/S30-AMQP-POISON-001/evidence/reference-target-proposal-2026-10-17.md), accepted [`spec.md`](../specs/S30-AMQP-POISON-001/spec.md), [P-005](../specs/S30-AMQP-POISON-001/design/prototypes/P-005/README.md), [P-007](../specs/S30-AMQP-POISON-001/design/prototypes/P-007/README.md), and [deferral decision](../specs/S30-AMQP-POISON-001/evidence/s30-06-deferral-decision-2026-10-18.md) | **DEFERRED / NOT QUALIFIED / design gate OPEN.** No wrapper/start guard exists; Docker daemon access exposes a bypass; P-007 stopped before runtime. Its runtime outcomes remain NOT RUN. Keep accepted requirements unchanged, consumer disabled, and do not claim REF-Q/PROD-Q. |
| S05-05a/05b synthetic database methodology and index evaluation | [`S05-05b-synthetic-experiment-profile-2026-10-17.md`](../../tooling/performance/S05-05b-synthetic-experiment-profile-2026-10-17.md), [`postgres_query_comparison.py`](../../tooling/performance/postgres_query_comparison.py), and [`evidence/S05-05b-2026-10-17/report.md`](../../tooling/performance/evidence/S05-05b-2026-10-17/report.md) with three compressed raw-plan archives | Synthetic only. The preselected offset-page case showed median p95 improvement 99.1% across three 25-sample runs; p95 is unstable at this sample count and this is an experiment observation, not a guarantee. Repeatable >10% PARKED-entry write-throughput regressions violate the guardrail. Decision: retain baseline, make no migration, and do not search for more indexes within S05-05b. The bounds are not production SLOs. The compared tables are UNLOGGED, and write throughput is derived from EXPLAIN ANALYZE UPDATE execution time; it is not durable end-to-end throughput. This limitation does not change the retain-baseline decision. |
| S05-07 status | This delta plus the master progress record | State exactly: **repo delta ready, external update pending**. Do not call external handbook synchronization complete until its owner updates and verifies the separate document. |

Source-binding note: the Showcase source SHA is `49ab97f580ad21c67e9378e15448aca9fe365c45` plus the pending local diff. The runner, S30 target proposal, scoped design revision, P-005 question/probe/result/errata/both reconnect follow-ups, owner checklist, profile, report and benchmark evidence are SHA-bound in `tooling/performance/evidence/S05-05b-2026-10-17/source-manifest.json`; the source remains uncommitted. P-007 and the 2026-10-18 deferral decision were added after that manifest was generated and are not covered by it; their current content remains local pending the owner’s review. `graphify update .` completed and its report says it was built from current HEAD `49ab97f5`; this records the base commit, not an independent digest of the dirty diff. Graphify also reported missing optional HCL/SQL parser dependencies, so Terraform/SQL symbols were not included. Apply handbook changes only after receiving a committed source SHA and verifying the manifest hashes. No external handbook was modified by this change.

## Harness 0.14.0 adoption delta (2026-10-20; repository source)

Source: Showcase adoption commit `89382cd24e3c8cd61dca6e6d9d4fb04f529b2a0e`, pinned to `agent-harness` v0.14.0 release SHA `cc9d33a4feac3f1462e7fe65cdabec3389a70f3a`; its exact-commit main CI run 39333226560 passed. This is an external-handbook delta only; it does not mark the separate handbook synchronized.

- `agent-harness` v0.14.0 (AH5-12b): the runner's agent launch goes through `agent_harness.execution.AgentCliBackend`, one launch path.
  - Before launch, the model is resolved explicitly (`--model` or configuration; otherwise the run is refused). `--agent-timeout` defaults to 1800 seconds. `--preflight` checks a run without launching; the orchestrator calls it before starting an attempt, so refusal does not use an attempt. Launch checks the material conditions again.
  - The run's own sandbox must block writes into the evidence directory. The directory must be unreachable by agent writes through real paths and symlinks. A primary checkout is refused with instructions to use a linked worktree.
  - Reviewer runs are read-only, including Claude's Bash.
  - Credential-shaped output or output over 8 MiB is withheld. `withheld.json` records the reason without the content; the real process outcome is preserved, and withheld output never produces a task result.
  - Claude restrictions were confirmed live only for Claude Code 2.1.289 on the tested macOS host (agent-benchmark `ab5-07-report.md`, commit `091ccb1`). The Showcase `denied_commands` list and reviewer tool set were confirmed only by Showcase's real-CLI check at adoption.
- Sources: `docs/specs/AH5-12/packet.md` and `docs/specs/release-0.14.0/packet.md` in `agent-harness`.
- Dependency Review was skipped on push-triggered CI and remains a recorded coverage gap. S30-06 remains `DEFERRED / NOT QUALIFIED`; the application AMQP consumer remains disabled.

External handbook owner: apply this delta against the committed adoption SHA and exact-commit CI evidence. Repository documentation being current does not mean the external handbook is synchronized.

## Harness 0.15.0 adoption delta (2026-10-20; repository source)

Showcase adopted agent-harness v0.15.0 release SHA
`717f4bab01c692963e6b66b680a947b45734226f` in commit
`68d22c7865bfabdae5c6ce793fa02f18ce27e4ec`. The release extracts the
validation portion of `verification_contract.py`; it does not change its
behavior. The Showcase compatibility shim binds `repository`, `constitution`,
`repo_root` and `is_active` when each validation function is called, preserving
the consumer's current configuration rather than capturing it at import time.
Showcase retains `generate`, `prompt`, `repo_root` and
`is_active_feature_dir`; `generate` remains a separate, unchanged launch path.

The 0.15.0 pin and shim passed the full Agentic SDD test command locally in the
pinned Python 3.13.16 image (924 tests). On the exact adoption commit, [main
CI run 39410269402](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269402),
[Agentic SDD CI run
39410269444](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269444),
[CodeQL run
39410269457](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269457),
[Scorecard run
39410269454](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269454),
and [Graph Update run
39410279276](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410279276)
succeeded. Dependency Review was skipped for the push event and is not counted
as a completed check. This repository delta does not mark the external
handbook synchronized.

## Harness 0.16.0 adoption delta (2026-10-23; repository source)

Showcase adopted agent-harness v0.16.0 release SHA
`fc4ad6471b9dcec65a17e61ab0975c1de14f79dc` in commit
`1b22802d52f59c1629e20edbc7ec7aaf09761d79` (parent
`36497846b0e23121ff1d094412b23be5eb19c998`). This release moves shared
feature-document validation, state paths and identity checks, and the single
state writer into the library. The Showcase `harness.py` is the release shim;
it builds a `FeaturePolicy` per call, binding Showcase's repository base,
active-feature predicate, protocol files, reviewer/profile policy, contract
validation, criterion IDs, and `.agent-state` location. The binding is not
captured at module import. `save_state` delegates to the library; Showcase has
no second state writer. Lifecycle transitions, admission, recovery, and CLI
remain Showcase-owned in this release.

Validation evidence: the complete Agentic SDD test suite passed in the pinned
`python:3.13.16-slim` image at digest
`sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f`
(924 tests). The `test_harness` module passed all 255 cases through the shim.
Consumer-path tests cover real Git worktrees sharing lifecycle state and its
cross-process lock, fail-closed behavior when the lock is unavailable, and a
lifecycle mutation holding the repository lock through state commit. A
disposable-clone rollback restored the adoption parent's tree exactly:
`4afaea1b02a54dba2dea98de9f1a4088fb04c8c2`.

The exact-commit GitHub workflows are recorded in the companion
[`0.16.0 adoption handover`](showcase-handover-ah5-14a-adoption-2026-10-23.md).
The direct push was authorized; GitHub reported that the push bypassed the
pull-request requirement, two required status checks, and verified-signature
requirement. Those bypasses are not counted as passed controls. Dependency
Review was skipped for the push event and is not counted as completed. This
repository delta does not mark the external handbook synchronized.

## Harness 0.17.0 adoption delta (2026-10-24; repository source)

Showcase adopted agent-harness v0.17.0 release SHA
`137b0ba057c4d69c94fc1d8b04b0af8d3b0b039e` in commit
`87e4ad688b17970dbd0afdc660d4a04ed6d0351e`. This release extracts the
lifecycle lock, state loading, completion-record readers, and the integrity
checks those operations require. It does not change behavior. The release
wrapper is mode `100755` with SHA-256
`0674b00bd623e9cb72c049b6d527a0682faee9e69d9b16cfe603e81a85a206e4`.
Showcase builds `StateAccess` per call. The binding passes the consumer's live
lock, common Git directory, fingerprints, save/load functions, pure state
reader, verification store, fault injector, incident lookup, and recovery-epoch
refusal callback. `_authority_transaction` is the same object for the library
and Showcase completion publishers. The incident producer is unchanged; the
separate fail-closed lookup correction remains pending a later Harness release
and a distinct Showcase adoption commit.

The complete Agentic SDD suite passed locally in the pinned Python 3.13.16
image: 924 tests. Exact-commit CI run
[40076326938](https://github.com/chrobakpiotr/showcase-application/actions/runs/40076326938)
failed on the pre-existing frontend audit and a Docker Hub `503 Service
Unavailable` while E2E pulled Keycloak. Backend, PostgreSQL/RabbitMQ, PIT,
OWASP, SBOM, Trivy, infrastructure validation, repository guards and docs
links passed. E2E was rerun on that same SHA and passed. Dependency Review was
skipped for the push event.

Follow-up commit `792dfade2775b9d3d6cef5b4020380d84fd1a2c4` updates the
frontend lockfile to remove the reported high findings without a forced
Angular major upgrade. The full [CI run
40077527346](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527346),
[Agentic SDD CI run
40077527344](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527344),
[CodeQL run
40077527462](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527462),
and [Scorecard run
40077527301](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527301)
passed. Frontend audit reports zero vulnerabilities; lint, 488 tests, build,
E2E and the aggregate quality gate passed. Dependency Review was skipped and
is not counted as passed.

Rollback rehearsal in a disposable clone reverted the adoption commit and
restored the exact parent tree `8ba523182e136b660500ffe3cb8b8484ae5c3897`,
including the full 0.16.0 pin `fc4ad6471b9dcec65a17e61ab0975c1de14f79dc`
and previous `harness.py` SHA
`06333e930bbbf5b71a8d7442bc31010aa04a71b17aa96065b544efb67602ff1a`.

The owner authorized the direct pushes. For each pushed commit GitHub reported
bypasses for the PR requirement, two expected required status checks, and
verified-signature requirement. These bypasses are not passed controls. The
full handoff and binding details are in the [Showcase 0.17.0 adoption
handover](showcase-handover-ah5-14b1-adoption-2026-10-24.md). Benchmark remains
on v0.14.0. The external handbook is still unsynchronized.

## Harness 0.18.0 adoption delta (2026-10-24; repository source)

Showcase adopted agent-harness v0.18.0 at full release SHA
`6d1c69a170dee8638b5535aed3f9aadb57845ba6`. The lifecycle/incident-adapter
cutover is commit `dfbcbbd8baa5ff74d54631869da13b0d50ad210f`; the separate
RetryControl API adoption is `422cc0c4e00717c16bc0a6023c214afbd8473b3c`; the
follow-up test-only assertion correction is
`ac552f0097214e339124a7f21b64174b2c0c4e3b`. The first exact-head Agentic SDD
run on `422cc0c` failed due to a misplaced assertion; it also caused eval A007
to fail. The assertion correction was verified with 101 A007 tests and 5/5
focused retry tests locally. On exact head `ac552f0`, main CI
[40104353741](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353741),
Agentic SDD [40104353595](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353595),
CodeQL [40104353607](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353607),
and Scorecard [40104353720](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353720)
all succeeded. Main CI's frontend audit/lint/tests, Playwright, backend, infra,
OWASP, Trivy, SBOM, PIT, repository guards, docs links, and aggregate gate
passed. Dependency Review was skipped on push and is not a passed control.
GitHub accepted the direct pushes while reporting bypasses of the protected-ref
rule, PR requirement, two expected required status checks, and verified-signature
requirement. These are not passed controls.

The lifecycle shim delegates the 14b-2 transitions to Harness. The shared
`_authority_transaction` remains the same library object used by Showcase
completion publishers. The required `task_workspace_snapshot`,
`restore_task_workspace`, and `restore_attempt_authorization` bindings and the
refuse-only recovery-epoch hook remain Showcase-owned. The separately reported
incident adapter now accepts only `None` as “no incident” and refuses invalid
lookup results before they can be treated as absence; the incident producer is
unchanged. Retry controls use the supported
`agent_harness.retry_control.RetryControl`, `inputs_digest()`, and a consumer-local
callback error mapped by Harness to `retry-policy-violation`. Both registered
control digests match their pre-adoption evidence.

Harness's historical 14b-2 report states 295/299 and identical base/wrapped
summaries of 921 ok, 2 FAIL, and 2 ERROR; Showcase does not hold the preserved
AH5-14 rehearsal packet or raw logs needed to reconcile those totals. The two
named baseline failures are
`test_harness.HarnessTest.test_register_manual_observation_serializes_scope_resolution_with_lifecycle_mutation`
and
`test_verification_completion_boundary.OriginCompletionBoundaryTest.test_real_plan_acceptance_cannot_be_completed_with_legacy_proofs`.
The report also mentions environment-only `test_verification_workspace` cases,
but their exact identifiers and results are not preserved here.
`test_verification_sandbox_doctor` does not collect as a test identifier and
is not counted as an unpassed test. Available evidence does not establish
whether it represents a command/discovery error or an undocumented case, or
how it relates to the reported totals. The 295/299 and 921/2/2 figures remain
historical Harness claims; the unreconciled portion of the denominator/results
is explicitly unconfirmed. Do not infer missing workspace test names or assign
missing results to the sandbox-doctor module. The historical report states
that 21 CI command outputs matched after masking and rollback passed.
Showcase's exact adoption CI status and disposable-clone rollback result are recorded in the
[adoption handover](showcase-handover-ah5-14b2-adoption-2026-10-24.md).

The evidence-correction commit `4d51618a5b05a57dee427596280d66804421ccc7`
passed CI (40133724720), Agentic SDD CI (40133724686), CodeQL (40133724715),
and OpenSSF Scorecard (40133724707) on that exact SHA. Dependency Review was
skipped for the push. The direct-push bypasses of protected-ref, PR, two
expected status checks, and verified-signature requirements were not passed
controls. No additional workflows were started solely for documentation.

Benchmark remains on v0.14.0; no re-pin or live trial is required for this
release. Dependency Review `SKIPPED` remains a coverage gap. S30-06 remains
`DEFERRED / NOT QUALIFIED` with its consumer disabled. The external handbook
remains unsynchronized.

## Historical workflow status and limits — through 2026-10-07

On pushed `a6fac3c33376`, the main CI aggregate and every required job
succeeded; dependency review was skipped for this push event. CodeQL and
Scorecard also succeeded. The separate Agentic SDD workflow failed because the
generated [`docs/specs/INVENTORY.md`](../specs/INVENTORY.md) did not match the
new S30 status text. The mismatch is fixed in pushed commit `98e733a`. The
latest pushed SHA `d0fb4e44d2fb` has Agentic SDD run
[37526914410](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914410)
and Scorecard run
[37526914005](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914005)
passed. CI run
[37526914133](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914133)
and CodeQL run
[37526914038](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914038)
remain in progress. Within CI, docs links, OWASP, PIT, frontend, Playwright
E2E, SBOM, Trivy, infra-as-config and repository guards passed; backend
build/quality/tests were still running; dependency review was skipped for the
push event. Do not describe every workflow as green until those runs finish.

The separate handbook owner should link each carried statement to the
corresponding accepted/published commit and its fresh evidence, retain the
limitations above, and avoid copying this file's local branch status as
current CI or production qualification.

### 2026-10-07 refresh

Remote CI run
[37593226168](https://github.com/chrobakpiotr/showcase-application/actions/runs/37593226168)
and its aggregate quality gate succeeded on pushed SHA `51bf45dfce56`;
Dependency Review was skipped because the run was not for a pull request. The
Showcase checkout was at local head `becfc64` when this delta was refreshed,
15 commits ahead of the fetched `origin/main`; the green run does not cover
those local commits. GitHub also reports the qualification fallback workflow
missing from the remote default branch. No push was performed.

### 2026-10-10 refresh

On pushed `main` SHA `830c5e6702a059f94b96ed8ebd4959b92b2d7d62`, the main CI
[run 38047027408](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027408),
Agentic SDD CI
[run 38047027482](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027482),
CodeQL
[run 38047027494](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027494),
and Scorecard
[run 38047027409](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027409)
all succeeded. The main CI covered backend, PostgreSQL and RabbitMQ evidence,
frontend and Playwright, OWASP DependencyCheck, SBOM, Trivy, PIT, infrastructure
validation, repository guards and documentation links. Dependency Review was
skipped for the push event. A separate manual Agentic SDD dispatch on the older
T-008 branch failed with stale inventory and harness-test errors; the branch
was seven commits behind current `main`, and those failures do not change the
successful main results. See the detailed
[`CI evidence record`](../ci/showcase-master-review-ci-evidence-2026-10-05.md).
