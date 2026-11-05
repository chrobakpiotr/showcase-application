# Harness 0.29.0 repository adoption (AH5-17 stage 2, ADR 0006)

Owner authorized release and adoption on 2026-11-01 (standing decision) and on 2026-11-04. Previous consumer checkpoint:
c574f0b734543ba5fe0d9b7bd9250cc553c9126f (pin 26aec0c, 0.28.0). New pin: aa7c943d2a657dd2c0bd6e270259d61156504015 (0.29.0), annotated tag `v0.29.0`, tag object 8dc1c9f19202bba09ed4344fcac92fe35257ccbd.

## Scope

decision map is now the decision map: `decision_map.py`, its schemas, agents, role files, template, example and CI step. Old
paths are read in place for one release; `decision_map.py` forwards with one deprecation line, and is removed in the next
release. `decision_map.py migrate` moves a map and its state, `--downgrade` moves them back. Historical records, eval ids
and digest-bound files (`docs/agentic-sdd/README.md`, `constitution.md`, `handbook.md`, the PDF) keep their bytes, so
that README still gives old-path commands. Protocol files are renamed, so the protocol fingerprint changes once.

## Evidence

- Harness exact release CI 41940947781 on the pin: success. Publication run 41941225044: success.
- Independent review: PASS after one amended round; faked launches only, no real provider CLI or model.
- Hosted rehearsal 41940104063: success with the released 0.28.0 and with this candidate, full consumer suite with
  test-count reconciliation.
- The exact adoption commit's main and Agentic SDD CI are the evidence for this commit.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store, staged
sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local activation requires
the exact adoption CI and a fresh active-operation/state check.

Rollback: first `decision_map.py migrate --downgrade <map-dir>` for every map written since adoption, then revert this
commit. No such map exists at adoption.

Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules (pull
request, verified signatures, required checks). That is not a passed branch-protection check.
