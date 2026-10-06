# P-004 Candidate C follow-up review — 2026-10-06

**Disposition:** documentation recommendation is internally consistent after
the user's direction to advance Candidate C as the recommended REF-Q working
design. This is not acceptance of the proposed `spec.md` amendment, a design-gate
PASS, implementation authorization, or REF-Q qualification.

## Scope reviewed

- `revision-proposal-2026-10-05.md` records Candidate C as the user-selected
  recommendation while keeping P-004 and the design gate OPEN.
- `plan.md` distinguishes the owner-selected recommendation from accepted
  policy and lists the remaining specification, gate, and target-evidence work.
- The P-004 evidence identifies supported REF-Q restore paths and explicitly
  excludes wrapper bypass, host loss/root compromise, full-host rollback, and
  direct/provider restore from the reference guarantee.

## Independent findings and disposition

- Architecture follow-up found no conflation between the user-selected
  recommendation and accepted-spec amendment, gate PASS, implementation, or
  qualification. It asked for precision that `design.json` already records C
  even though the gate is still OPEN; `plan.md` now says the accepted spec is
  unamended and the gate remains OPEN.
- Scope grill found that the earlier plan checklist unconditionally required
  read-only mount identity and per-permit host-record read measurements. Those
  requirements are specific to a mechanism that mounts/reads the record on the
  admission path. The plan now makes them conditional and names Candidate C's
  process-manager fencing, exact-episode startup binding, crash-cut coverage,
  and shutdown/restart/recovery cost evidence.
- Both reviewers confirmed the corrected checklist and found no remaining
  issue in this bounded documentation decision. They did not assess an exact
  REF-Q implementation or qualify any environment.

## Remaining work

The scoped REF-Q DR text in the accepted spec has not been amended. Obtain any
required acceptance and fresh independent gate review before generating
implementation tasks. Then perform exact-target prototype/failure tests and
qualification. Keep all consumers disabled until the required gates pass.
