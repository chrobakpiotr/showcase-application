# Dependency Review Action — decision package status

## Current state

The current local successor is `fcbc44201b365daec4da85a4cf73adde8b82609c`, tree
`1c6d80541204acda361e169916136a9381ecaaa8`, based on the previously reviewed candidate
`4665869fdfe9557a437e61fdc9667b82e5bb414d`. Its durable candidate bundle, source patch, build input,
distribution, test logs, workflow patch, and checksums are in
[`successor-fcbc442/`](successor-fcbc442/README.md).

The `4665869` review was performed by the separate Showcase review agent
`/root/independent_dependency_review`; the exact report and scope are recorded in
[`INDEPENDENT-REVIEW.md`](INDEPENDENT-REVIEW.md). No Harness review report for that candidate is
preserved. The earlier statement attributing that PASS to Harness was wrong and is withdrawn; see
[`N1-CORRECTION.md`](N1-CORRECTION.md). The previous candidate's PASS is not transferred to the successor.

Harness independently reviewed exact successor `fcbc44201b365daec4da85a4cf73adde8b82609c`
at [agent-harness commit c96f5e66275b203b96cf40b2e96b20fbc350eb39](https://github.com/chrobakpiotr/agent-harness/blob/c96f5e66275b203b96cf40b2e96b20fbc350eb39/docs/reviews/dependency-review-action-fcbc442/README.md),
with verdict **PASS WITH AMENDMENTS**; F5 is closed. The
[dated correction](REVIEW-CORRECTION-2026-10-26.md) explicitly supersedes conflicting ownership,
verdict, digest, license-path, test-count/warning, and complete-history claims in the historical
reports and successor package. Those checksummed artifacts remain unchanged.

The master closeout disposition accepts that external erratum for N1/N2/N3/D1 instead of changing
the reviewed action SHA. This is a separate disposition, not a revised Harness verdict: Harness
originally requested candidate-internal documentation updates, and its N4/F6 stale-documentation
finding and optional F7 remain recorded. The parallel Jest worker warning remains reproducible,
cause unknown. [Full-history reconstruction evidence](full-history-reconstruction/README.md)
now records a successful complete-history replay of the same candidate and tree.

The historical `OWNER-PACK-SHA256SUMS` describes the prior owner decision edition and intentionally
does not validate this revised live file. Use the separately dated
`OWNER-PACK-SHA256SUMS-2026-10-29` for this edition.

## Decision requested after review

After Harness reviews the exact successor and its findings are closed, the owner may decide whether
to authorize (a) creating and publishing `chrobakpiotr/dependency-review-action` at the exact
reviewed commit, (b) Harness accepting ongoing patch maintenance, and (c) a separate Showcase
workflow activation commit. This document is not that authorization. No fork, publication, PR, or
workflow activation has occurred. The active Showcase workflow remains unchanged.

The workflow patch is based on Showcase commit
`5394efc17bedf8cf55f4a938924684685b252dde` and pins the exact candidate SHA. After a separately
authorized publication and activation, the first actual GitHub run must verify runner compatibility,
the full `before → sha` push range, and the artifact upload path. Private-repository 403 and live
GHES behavior remain unverified. Rollback is a dedicated revert of the activation commit, restoring
the official action workflow; preserve any published fork history.

