# Harness 0.31.0 repository adoption

Owner order 2026-11-05 (remove the former name of the decision map; finish the flaky test and the dead commands) and the standing decision
of 2026-11-01. Previous checkpoint 9520cabe209317cad20aea771736c1fb946affbd (pin ed40f73, 0.30.0). New pin: a0ea41125df252af8231e8c7fc578167393b9f69 (0.31.0), annotated tag
`v0.31.0`, tag object a0abf896bcb4221a65f81e20a8da99613e0cb2c5.

## Scope

Old-name compatibility is gone: `migrate`, the refusals and the legacy-claim guards. **Old maps, handoffs and claims are now ignored, not
refused.** Before updating a used checkout, follow the steps in Harness `docs/specs/release-0.31.0/packet.md`. README, handbook and PDF no
longer use the former name, and the dead commands are gone. Records no digest binds were rewritten mechanically, so they are no longer
verbatim; Git history holds the originals, and Harness `docs/specs/AH5-17/name-removal-2026-11-05.md` lists what could not change. Protocol
files change, and the fingerprints of three features change; no committed file cites those.

`t008-verification.json` no longer matches README, handbook or PDF: two baseline entries record the first two, and the PDF's key is one the
guard does not read.

## Evidence

Harness exact release CI 42103851274: success. Publication run 42104147930: success. Independent review: PASS after two amended rounds. Hosted rehearsal
42102772007: success on both sides. The exact adoption commit's main and Agentic SDD CI are the evidence for this commit.

## Local activation and rollback

Repository adoption only. Rollback reverts this commit; a map written since then stays readable, since 0.30.0 reads the new paths. Pushes
from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules. That is not a passed check.
