# S30-06 prototype review evidence — 2026-10-05

These are disposable design experiments, not service implementation and not a
design-gate PASS. `design.json` predeclares P-001 through P-003 and their
criteria. The reproduction code uses only local/container fixtures.

## P-001 — leader fence and Redis epoch

`gate-fencing-probe.py` reproduces the PostgreSQL row-lock and Redis epoch
primitives. A later comparative scratch run measured 2.80 seconds waiting for
the row-lock candidate and 2.76 seconds for the advisory-lock candidate under
similar but separate schedules. The experiments are not comparable end-to-end:
they did not exercise a single gate operation spanning PostgreSQL owner/lease
validation, Redis mutation, `WAITAOF`, uncertain response, and permit decision.
The Redis order test showed that epoch 1 is rejected after epoch 2 is installed,
but can mutate state before epoch 2 installation. The required recovery rule is
therefore sticky inhibit plus reconciliation before admission. Independent
prototype evaluation marked P-001 **needs more evidence**; neither lock
candidate is selected by these measurements.

## P-002 — command result retention

`p002-command-retention-prototype.py` is a SQLite model. It exercises exact
retry, changed-request rejection under the original envelope, mutation,
expired-envelope rejection after restoring an old row, key retirement, and the
counterexample where a newly signed envelope reuses a caller-selected ID after
the old row has expired. For 36,500 rows, the final run reported 7,434,240
bytes after audit deletion and compaction with indefinite minimal tombstones
(203.7 SQLite bytes per row), versus 24,576 bytes with zero rows after a
30-day purge. These figures are illustrative only; they are not Redis,
PostgreSQL, WAL, encrypted-volume, or backup estimates.

A gate-minted immutable envelope avoids same-ID reissue only if the client
receives it before execution and retries that exact envelope. A lost envelope
issuance response still needs an idempotent contract. The current candidate
retains a minimal tombstone independently of the one-year actor/reason audit;
this is a proposed decision, pending privacy, key-retention, backup, and
restore review. The SQLite model does not qualify that decision.

## P-003 — permit and identity state model

`p003-permit-state-model.py` is a pure Python model. It compares the accepted
five-second offline-permit window with immediate fail-closed behavior for an
online check. It is intentionally not cryptographic or a runtime test. Its
identity authorization helper decodes JWT claims without verifying the
signature; the checked-in counterexample mutates the payload and keeps the
invalid signature, demonstrating that the helper accepts forged identity
claims. Permit HMAC/replay assertions likewise do not establish asymmetric
key custody, OIDC/JWKS, cross-process replay, handler-start synchronization,
or suspend/resume timing. The independent evaluator marked P-003 **needs more
evidence**. Do not use the model's authorization results as security evidence.

## Independent review result

The architecture grill returned **NEEDS_MORE_DESIGN**. Security returned
**FAIL / security-design-gated**. Persistence/concurrency returned
**NEEDS_MORE_DESIGN**. A follow-up independent rereview of commit `78f828c`
confirmed that several contracts are clearer on paper, but still withheld
approval. The remaining findings and our current response are:

- Signed permit claims must include the request nonce; registration/drain must
  be bound to a unique instance identity and broker-observed connection. The
  candidate now requires a signed nonce and records unique identity binding as
  an unresolved release requirement. Provider-backed identity and connection
  proof remain untested.
- Every authenticated retry must be actor-attributed before returning the old
  result. The candidate now specifies a separate durable retry audit event;
  atomicity and outage behavior remain to be proven.
- Rabbit fencing needs tool-only connection inspection/close capability that
  cannot read quarantine messages. The candidate now explicitly blocks this
  capability until an ACL probe demonstrates the separation; no accepted
  least-privilege implementation is established yet.
- Cross-store recovery must handle Redis-ahead-of-PostgreSQL, an uncertain
  `WAITAOF`, expected one-year audit expiry, and coordinated rollback of both
  stores. The candidate now enumerates these fail-closed cases, but the
  monotonic external witness and provider-level reconciliation are unresolved.
- The 06b startup dependency needs an executable, versioned capability check,
  not a configuration flag. Its owner and enforcement seam remain open.
- Tombstone/reason privacy and KMS lifetime require an explicit decision;
  design/spec wording must continue to distinguish accepted policy from this
  unaccepted candidate.

The deterministic [cross-store schedule model](recovery-schedule-prototype.md)
now exercises the intended fail-closed outcomes for Redis commit followed by
lost PostgreSQL finalization, a lost `WAITAOF` reply, and coordinated stale
restore. It confirms a local-information gap for joint rollback; it does not
prove provider reconciliation or durability.

The [Rabbit fencing capability decision](rabbit-fencing-capability-decision.md)
found that built-in user tags do not grant narrowly scoped inspect-and-close
permissions for another user's consumer connection. A deployment-owned
fencing proxy/service or RabbitMQ authorization extension is required, with a
separate broker identity that cannot access quarantine messages. A disposable
RabbitMQ 4.3.6 broker probe confirmed: monitoring could inspect but not close;
an administrator-tagged user with empty resource permissions could close the
exact connection and was denied message retrieval, but could still list users.
This demonstrates the proxy/plugin requirement, not least-privilege
management. Exact instance-to-connection binding and deployment-version ACL
tests remain open.

A fresh security rereview of `c439429` confirmed partial closure on paper but
kept the security gate **FAIL**. It found the nonce and retry-audit proposals
clearer, while requiring runtime tests and preserving identity binding, Rabbit
capability, and cross-document status as blockers. Spec/plan now explicitly say
the detailed claims, roles, and schema in the candidate are proposals, not
accepted contracts. This does not authorize task generation or produce a
design-gate PASS. Required next evidence remains real OIDC/JWKS and permit
validation, persistent replay and suspend/resume tests, Rabbit drain/reconnect
and ACL tests, encrypted storage, audited read/export, backup deletion, and
sentinel-data leakage checks.

The architecture grill's review at `b183d10` agrees the policy/proposal wording
is now clear and the Rabbit role limitation is evidenced. It keeps the design
gate at **NEEDS_MORE_DESIGN**: the 06b capability signal and fence mechanism,
P-002 retention choice, per-instance identity bridge, and provider-level
reconciliation/anti-rollback proof remain open. The phrase “accepted retry
identity” in the candidate was corrected to distinguish accepted stable
command-ID behavior from the still-unaccepted indefinite tombstone proposal.
