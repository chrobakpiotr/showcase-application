# Harness 0.31.1 repository adoption

Owner order 2026-11-06 ("Fix and release now", then "Release with local gates" while GitHub Actions is blocked by the
account's spending limit until 1 December) and the standing decision of 2026-11-01. Previous checkpoint
67d55ba (pin a0ea411, 0.31.0). New pin: 7da5d43c14461b007cedcbd7edaedaae53f31e86 (0.31.1), annotated tag `v0.31.1`, tag
object 839b064cbedbcf6bf6a5f9f8e2e31cab72ed3fe9.

## Scope

Plan acceptance and the completion origin check now reconstruct a task's verification plan from its worktree, not
from this checkout. Before, every orchestrated task that changed a file stopped with `invalid-verification-plan`
(FIRST-RUN-002 on the owner's Mac; the line dates from `36a1022f`, 2026-09-30). A worktree path that is a symlink or
belongs to another repository is refused with `VERIFICATION_CANDIDATE_WORKTREE_INVALID`. No consumer interface
changes. This commit changes the pin, `PINNED_HARNESS_VERSION` in `qualification/report.py` and that test's version
asserts. The pin is part of the protocol fingerprint, so in-flight features re-validate; FIRST-RUN-002 is not
retried.

Known limit, unchanged: a repeated `complete` or a `complete-repair` after the checkpoint commit still fails the
origin check (Harness `docs/specs/release-0.31.1/packet.md`).

## Evidence

Hosted CI did not run (owner's exception). In its place: Harness library suite and Showcase's harness suite (990
tests) with 0.31.1 in this session; `scripts/check-wheel.sh` on the owner's Mac on the release commit (967 of 968; the
failing test reads `/proc/self/fd`, absent on macOS, unchanged since 0.31.0); independent review PASS (static-capped)
and a delta review PASS; publication checked by hand (Harness `docs/specs/release-0.31.1/publication-verified.json`).
This commit's CI runs when Actions is available again. The owner's install and harness suite on the Mac, then
FIRST-RUN-003, are the activation evidence.

## Local activation and rollback

On the Mac: `git pull`, `pip install -r tooling/agent-harness/requirements.txt` in the venv, `doctor`. Rollback reverts
this commit and reinstalls. Pushes from this session are ordinary non-force pushes.
