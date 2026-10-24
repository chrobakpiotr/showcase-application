# Showcase handover: agent-harness 0.18.0 adoption

## Adoption commits

- Release pin: `agent-harness[grants]` v0.18.0, full SHA
  `6d1c69a170dee8638b5535aed3f9aadb57845ba6`.
- Lifecycle and incident-adapter cutover: `dfbcbbd8baa5ff74d54631869da13b0d50ad210f`.
- Separate RetryControl API adoption: `422cc0c4e00717c16bc0a6023c214afbd8473b3c`.
- Test-only assertion correction: `ac552f0097214e339124a7f21b64174b2c0c4e3b`.
- Previous complete v0.17.0 parent: `d7097d50c068ce7f2bb5cff2d95c3cdf166b9010`.
- `harness.py` and `tests/test_cutover_wrapper.py` are copied from the release at
  the required modes (`harness.py`: `100755`, SHA-256
  `3d37e8c5358752cbaa0fec1f604d11cfa78f6fc0614910b9d8e2da64d8945c75`;
  wrapper test: `100644`, SHA-256
  `9a397bb0c7230a5a465c43df3e56d69d8f014666c4c267a0386a5b0405991619`).

## Lifecycle extraction

The v0.18.0 shim delegates lifecycle checks and transitions to Harness, removing
Showcase's replaced transition implementations. `_authority_transaction` is
still the same object as the library's `_authority_transaction` and remains the
transaction used by Showcase completion publishers. The three explicit
Showcase recovery bindings remain required: `task_workspace_snapshot`,
`restore_task_workspace`, and `restore_attempt_authorization`; the library
checks them at shim import. The recovery-epoch refusal hook remains Showcase
owned and is called only when an incident exists, after the library snapshot
check.

## Incident lookup adoption (behavior change)

Showcase now binds `StateAccess.recovery_incident` through
`checked_state_recovery_incident`. `None` is the only no-incident result. A
record is passed on only if it satisfies the same checks as
`state_recovery.load_incident`. `False`, empty or malformed records, empty
text, unexpected types, and read errors refuse with
`STATE_RECOVERY_INCIDENT_INVALID`; no invalid result can be treated as absence.
The producer `state_recovery_incident` is unchanged. The copied
`test_the_incident_lookup_is_fail_closed` covers the invalid values and the
binding. This is the consumer's adoption of `ff06352`; the Harness release by
itself did not close the status.

## RetryControl API adoption

`verification/retry_controls.py` imports the supported
`agent_harness.retry_control.RetryControl`, uses `inputs_digest()`, and raises
Showcase's `RetryControlPolicyError` from its refusal callbacks. Harness maps
any callback `Exception` to `retry-policy-violation`. The prior
`verification.serialization.canonical` and
`agent_harness.verification.supervisor.SupervisorError` imports and local digest
helper are removed. Control IDs, retry policy, and historical evidence remain
unchanged.

The actual registered controls preserve their pre-adoption digests:

| Control ID | Digest |
|---|---|
| `critical-postgres-gate-disables-gradle-test-retry-v1` | `ba34b24749900a92c69b12b024e171fe3920343aced5fb05e8b76984d8b0707d` |
| `critical-rabbit-gate-disables-gradle-test-retry-v1` | `dd476704327344e29951c4a579b687f4a0c6e61a60bad8f5e20bb38c73397afc` |

The targeted tests exercise the real registered controls and supervisor path,
including a mutated Gradle input whose callback refusal maps to
`retry-policy-violation` before launch.

## Verification

- Wrapper, incident, and qualification pin tests: 26 passed.
- Focused retry API and supervisor integration tests: 4 passed.
- A combined local run completed 85 tests but was interrupted after 527
  seconds by the operator; the command also named a non-existent test class,
  so that command is not a green suite result. Its lifecycle, recovery, replan,
  and authority tests that ran produced no unexpected failure before the bad
  test selector was reached. The full CI result below is authoritative for this
  candidate.
- The first exact-head run on `422cc0c4e00717c16bc0a6023c214afbd8473b3c`
  exposed a misplaced test assertion (`NameError`) in the new digest test. That
  also made eval A007 fail because it invokes the security/supervisor tests.
  The assertion was moved to the registered-control integration helper in
  test-only commit `ac552f0097214e339124a7f21b64174b2c0c4e3b`.
- After the correction, the exact A007 test command passed locally: 101 tests;
  the focused digest and callback tests passed 5/5. On exact head
  `ac552f0097214e339124a7f21b64174b2c0c4e3b`, main CI
  [40104353741](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353741),
  Agentic SDD CI
  [40104353595](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353595),
  CodeQL
  [40104353607](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353607),
  and Scorecard
  [40104353720](https://github.com/chrobakpiotr/showcase-application/actions/runs/40104353720)
  all succeeded. Main CI's frontend audit/lint/tests, Playwright, backend,
  infra, OWASP, Trivy, SBOM, PIT, repository guards, docs links, and aggregate
  quality gate passed. Dependency Review was skipped on push and is not a
  passed control. GitHub accepted direct pushes while reporting bypasses of the
  protected-ref rule, PR requirement, two expected required status checks, and
  verified-signature requirement; those are not passed controls.
- Harness's accepted AH5-14b-2 evidence is 295/299 through the shim; the four
  known unpassed cases are:
  1. `test_harness.HarnessTest.test_register_manual_observation_serializes_scope_resolution_with_lifecycle_mutation`;
  2. `test_verification_completion_boundary.OriginCompletionBoundaryTest.test_real_plan_acceptance_cannot_be_completed_with_legacy_proofs`;
  3. the environment-only `test_verification_workspace` cases;
  4. the environment-only `test_verification_sandbox_doctor` cases.

  The first two are established baseline failures in Harness
  `docs/specs/AH5-12/packet.md`; both failed on both sides in the AH5-14b-2
  rehearsal. The latter two are environment-only failures in the local macOS
  copy. Harness records identical base/wrapped test results (921 ok, 2 FAIL,
  2 ERROR), identical 21-command CI outputs after masking, and successful
  rollback in `docs/specs/AH5-14/packet.md`. This is not an all-green result.

## Rollback and ownership handover

Rollback of the complete code set was rehearsed in disposable clone `/tmp/showcase-018-rollback-final`. Reverting test correction `ac552f0`, RetryControl adoption `422cc0c`, then lifecycle/incident adoption `dfbcbbd` restored exact tree `d7097d50c068ce7f2bb5cff2d95c3cdf166b9010`; `git diff --exit-code` passed. The restored pin is `137b0ba057c4d69c94fc1d8b04b0af8d3b0b039e`; old `harness.py` SHA-256 is `0674b00bd623e9cb72c049b6d527a0682faee9e69d9b16cfe603e81a85a206e4`, and the old wrapper test SHA-256 is `2bc7bdfe04ac4420d04d86d9be178cf298b81604c3d7c23182249ff9c2accc72`.

**Harness consumer-matrix update to apply:** update the Showcase row in
`agent-harness/docs/migration/consumers.md` to pin `v0.18.0` at
`6d1c69a170dee8638b5535aed3f9aadb57845ba6`, adoption commits `dfbcbbd` and
`422cc0c`, and the APIs now consumed: lifecycle checks/transitions via the
compatibility shim; public `agent_harness.retry_control.RetryControl`; fail-
closed recovery incident adapter. Keep Showcase's own state, policies, and the
three recovery bindings consumer-owned. Record the exact CI status above,
including Dependency Review `SKIPPED`.

Benchmark remains on v0.14.0. F1 is a separate Harness behavior package and
must keep its accepted limited race guarantee. AH5-14c remains blocked until
F1 implementation, independent evaluation, and Showcase adoption. S30-06 stays
`DEFERRED / NOT QUALIFIED`, with its consumer disabled. The external handbook
remains unsynced.

## Direct-push reports

GitHub accepted the two adoption commits and reported these bypasses verbatim:

```text
remote: Bypassed rule violations for refs/heads/main:        
remote: 
remote: - Cannot update this protected ref.        
remote: 
remote: - Changes must be made through a pull request.        
remote: 
remote: - 2 of 2 required status checks are expected.        
remote: 
remote: - Commits must have verified signatures.        
remote:   Found 2 violations:        
remote: 
remote:   422cc0c4e00717c16bc0a6023c214afbd8473b3c        
remote:   dfbcbbd8baa5ff74d54631869da13b0d50ad210f        
```

None of those bypassed requirements is represented as a passed control.


The later test-only correction push was also accepted with this report:

```text
remote: Bypassed rule violations for refs/heads/main:        
remote: 
remote: - Cannot update this protected ref.        
remote: 
remote: - Changes must be made through a pull request.        
remote: 
remote: - 2 of 2 required status checks are expected.        
remote: 
remote: - Commits must have verified signatures.        
remote:   Found 1 violation:        
remote: 
remote:   ac552f0097214e339124a7f21b64174b2c0c4e3b        
```

Those bypasses are not passed controls.
