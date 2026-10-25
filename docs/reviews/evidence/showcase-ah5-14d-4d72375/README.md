# Showcase AH5-14d integration evidence

This packet preserves the focused local integration evidence for Harness
candidate `4d7237585e12ebec89a42d0325d34d354cc99783`. It is not a cutover
approval and does not replace the required fresh read-only check of
authoritative recovery state, executions, leases, and verification ownership.

## Source checkpoints

- Harness candidate: `4d7237585e12ebec89a42d0325d34d354cc99783`.
- Showcase source checkout used for the isolated integration copy:
  `1beddbe86e9d2d81e8e423aaf8d197759684b3c7`.
- The copied Showcase tree was based on `1beddbe`; the candidate shim and
  `test_lifecycle_recovery_binding.py` were overlaid for integration checks.
- Active Showcase pin remained agent-harness `0.20.0`; no cutover was made.

## Results

- `integration.log`: four focused `RecoveryBindingTest` cases passed. They
  check the shim delegates without recursion, rollback restores a grant via
  the library, reopen snapshots descendants via the library, and failed prune
  restores the workspace via the library.
- `ownership-orchestrate.log`: the focused busy-verification ownership test
  passed and reported `VERIFICATION_OWNERSHIP_BLOCKED: busy` before outcome
  mutation.
- `ownership-setup-error.log`: this attempted command did not run the requested
  tests. Collection failed because the isolated environment lacked
  `cryptography`; a second requested class name was misspelled (`OrchestrateTests`
  instead of `OrchestrateTest`). This is setup/collection evidence only and
  says nothing about candidate behavior. It is not counted as a test failure
  for the candidate.

The broader integration class had previously passed 21 tests in 175.261
seconds, but its raw log was not retained; this packet does not represent that
run as reproducible raw evidence. No broad suite was rerun for this packet.

## Scope limits

This evidence confirms real namespace delegation and one busy-ownership
refusal path. It does not close the requested gap for
`cmd_resume_verification` invoking the real
`assert_repository_verification_drained` barrier. The barrier test must prove
no lifecycle, grant, or other durable mutation on refusal, and must be
mutation-sensitive. AH5-14c-T1's remaining admission/terminalization mutations
also remain open and must be mapped separately.

No live recovery state was read or modified. No telemetry files or changes are
included in this packet.

## Integrity

`SHA256SUMS` contains hashes for the preserved raw logs. Hashes are computed
over the exact copied bytes.
