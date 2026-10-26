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
[`N1-CORRECTION.md`](N1-CORRECTION.md). The new `fcbc442` successor has not yet received Harness
review. The previous candidate's PASS is not transferred to it.

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
