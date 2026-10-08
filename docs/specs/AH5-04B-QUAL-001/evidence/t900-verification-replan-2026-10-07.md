# T-900 verification command replan

Date: 2026-10-07

The original T-900 packet required
`tooling/agent-harness/qualification/check_selected_report.py`, which does not
exist in the repository. The first review attempt therefore produced a valid
independent review but could not complete through the declared task protocol.

Using the harness `replan-task` compare-and-swap protocol, only T-900's
verification command was replaced with
`docs/specs/AH5-04B-QUAL-001/evidence/reviews/verify_github_runner_report.py`.
The acceptance criteria, role, dependencies, allowed paths, objective, and
qualification policy were unchanged. The replacement checks all 26 job-bound
evidence files and hashes, reruns the pinned Harness v0.3.0 checker, binds the
separate review to `review_subject(report)`, and treats exit code 1 as valid
evidence of a correctly checked but nonpassing report.

- Superseded packet revision: `sha256:745b404cc9d98be61a770841b03dbfd274facad31942eb72375aadb03ea988e0`
- Active packet revision: `sha256:5037678c80ac8fa8b55f9ed7c493738ebf4dfa140fcf0ea1758431ff8c034e38`
- T-900 attempt 1 was terminated as `REPLAN_SUPERSEDED`; attempt 2 completed.
- Verifier and independent review commits in the T-900 task worktree: `24734303bb023316d6473f161365f0f762d22782`, `b90421735a08af7713571af32941f9d85f0dd75d`.
- Full compare-and-swap proposal: [`replan-T-900-verification-command-2026-10-07.json`](replan-T-900-verification-command-2026-10-07.json).

The GitHub-hosted target remains **NOT QUALIFIED**. This task correction does
not change or weaken any qualification result.
