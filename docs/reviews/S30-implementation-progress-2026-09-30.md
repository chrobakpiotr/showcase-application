# S30 review implementation checkpoints

Base: `d371d6f36c7100369384668ce54723d1d30631f3`.
Source: `showcase-review-agent-plan-2026-09-30.md`, supplied by the user.
The initial worktree contains an unrelated untracked `adapter/` directory; it is outside scope.

## S30-01a task packet: preserve applicable profile requirements

Authority: SDD-OBS-001 `spec.md`, M53-AC37 and OBLIGATION-02/03/05;
`plan.md`, BQ-02 and R3-03. This checkpoint restores existing specified behavior;
it does not revise the accepted spec or its historical hash-bound artifacts.

Allowed paths:

- `tooling/agent-harness/verification/planner.py`
- `tooling/agent-harness/tests/test_verification_profile_showcase.py`
- this progress report

Invariant: task command mapping borrows profile policy but cannot suppress a separately
applicable mandatory profile node. Duplicate task occurrences retain separate identities.
Counterexample: a domain source change plus the mapped Gradle task command currently
produces only the task occurrence for the build, omitting the applicable profile node.

Test mode: red-green regression at the real Showcase profile / required-nodes seam.
Acceptance: applicable mapped gate produces one profile node plus each task occurrence;
non-applicable mapping produces only task occurrences; integration planning remains
profile-based; publication retains separate obligation IDs and one-member units.

This is only the first S30-01 checkpoint. Required origin/capability schema, pre-launch
admission, receipt and completion origin validation remain open. Extra nodes alone
must not be described as independent-origin qualification or complete S30-01 acceptance.
Coalescing is deferred. S30-02–10 remain pending in the supplied dependency order.
Historical closure evidence and remote configuration remain unchanged.

## Review recheck and checkpoint evidence

- F01: reproduced before the edit using the real Showcase profile. One mapped
  occurrence yielded one build node instead of two; two occurrences yielded two
  instead of three. Both assertions were RED under Python 3.12.
- F03: shared `verification_argv` still rejects all three critical verifier
  scripts as `command-not-allowlisted`; the other four profile commands are admitted.
- F04: persistence-only, AMQP-only, foundation-only and the actual PIT config
  path each still select only `showcase-gradle-build`. These remain open.
- S30-01a: removed mapping-based suppression of applicable mandatory profile nodes.
  No command authorization, sandbox permission or origin classification changed.
- Local checkpoint command: `docker run --rm -v /Users/pau/IdeaProjects/showcase-application:/workspace:ro -w /workspace python:3.12 python -m unittest discover -s tooling/agent-harness/tests -p test_verification_profile_showcase.py`.
  Exit 0; seven tests passed. The host's Python 3.9 cannot import the harness.
- Independent evaluator: PASS for S30-01a only; independently reproduced the
  original omission and passed 23 relevant profile/parity/authority tests in
  Python 3.12 Docker. Full S30-01 is not approved.
- Final SHA: none; changes are uncommitted. No new remote CI run or integration.
- `git diff --check`: exit 0.
- Full harness discovery with the same Python 3.12 read-only container mount,
  pattern `test_*.py`: reported failures/errors; interrupted with exit 130 before
  a final summary. This is incomplete evidence, not a full-suite PASS. Failure
  attribution remains unresolved; do not transfer the historical 577-test PASS.
- `./gradlew test`: exit 1 after 7m 50s on Java 26.0.2 / Gradle 9.7.1.
  Frontend npm install exited 243; frontend tests/lint exited 127 (`ng` missing).
  Domain test output also lost its `in-progress-results-generic.bin` file.
  The agent incorrectly launched `./gradlew build --continue` concurrently;
  shared build outputs make this domain result unsuitable for attribution.
- `./gradlew build --continue`: interrupted, exit 130, to stop overlapping
  execution. Repository-wide validation must be rerun sequentially on a prepared
  host before integration. These broad checks do not authorize this checkpoint's
  integration and were not used by the independent evaluator as passing evidence.

## Next checkpoint

S30-01b must specify the versioned origin/capability model and migration boundary
before editing shared authority: required origin on every obligation, exact unit
membership, explicit profile permission for the independent execution class, and
pre-launch immutable origin binding. Tests must attack admission, terminal receipt
and completion together, including missing/unknown origin and post-launch relabeling.
Historical v1 plans must not gain new authority through defaults.

Then follow S30-02a (non-reusable terminal success), S30-02b (execution outputs),
S30-03 (qualified-host spike and production wiring), S30-04 (command/profile policy),
S30-05 (positive-path CI), and final S30-09 closure. S30-06–08 application work follows
the harness delivery; S30-10 remains dependent on its operational prerequisites.
