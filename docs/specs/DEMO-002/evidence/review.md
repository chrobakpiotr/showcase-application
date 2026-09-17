# Independent review

Base: `da6c5fc` (confirmed against origin/main on 2026-09-17).

Persistence/concurrency reviewer: PASS after fixes. The review required sequence
restart beyond all old legal IDs, exact UTF-16 fingerprint encoding, and real
transaction tests for rollback waiters and stale takeover. It did not execute
Gradle and does not substitute for the integration gate.

Security reviewer: PASS after exact-code-unit encoding and regression for a lone
surrogate versus a question mark. Reviewed token origin/path restriction,
immutable retry snapshots, unknown outcome lock, and derived-field exclusions.

Frontend evaluator: PASS after two additional findings were fixed:

- Initial typed stock rejection must permit form correction; an already unknown
  attempt remains locked even after this rejection.
- Notifications, Shipments and Coupons layout tests must wait for their response
  and matching rendered table rows, not a loading selector absent from those pages.

Regression evidence for the stock rejection correction: 2 failing tests with the
old behavior, then 383 passing tests and 100% coverage in all categories. Security
and persistence reviewers performed read-only source reviews; execution results
are recorded separately.
