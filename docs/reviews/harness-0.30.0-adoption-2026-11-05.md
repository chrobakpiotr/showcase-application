# Harness 0.30.0 repository adoption (AH5-17 stage 3)

Owner authorized release and adoption on 2026-11-01 (standing decision) and on 2026-11-04. Previous consumer checkpoint:
3db5c9847ff577647442b4d4a3847ee240eb6557 (pin aa7c943, 0.29.0). New pin: ed40f73ca33d300263c2112295d7ac1e107adaf9 (0.30.0), annotated tag `v0.30.0`, tag object 98ec0b694b2b27135eb531d093f5517739d97e17.

## Scope

`wayfinder.py` is removed and old-path maps and state are refused with `DECISION_MAP_MIGRATE_REQUIRED`; `decision_map.py migrate`
is the one reader. `harness.py` now passes its bootstrap allowlist, legacy snapshot list, attestation owner and doctor table to
the library, with unchanged values, pinned by `tests/test_consumer_bindings.py`. Protocol files change, so the protocol
fingerprint changes.

**Known stale:** the digest-bound `docs/agentic-sdd/README.md` keeps its bytes, so its `wayfinder.py` commands no longer run. Use
`decision_map.py`.

## Evidence

- Harness exact release CI 42065411975 on the pin: success. Publication run 42065860444: success.
- Independent review: PASS after one amended round; faked launches only, no real provider CLI or model.
- Hosted rehearsal 42064277417: success with the released 0.29.0 and with this candidate.
- The exact adoption commit's main and Agentic SDD CI are the evidence for this commit.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store, staged
sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local activation requires the
exact adoption CI and a fresh active-operation/state check. Test runs and earlier use can leave
`.git/showcase-operational-v1/wayfinder/<epic>/`, which now refuses; `decision_map.py migrate docs/decision-maps/<epic>` clears it
with a backup, also when there is no map.

Rollback reverts this commit. Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the
branch rules. That is not a passed branch-protection check.
