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
retry, changed state-transition rejection under the original envelope,
reason-only same-ID retry returning the original generation with a separate
actor/reason event, expired-envelope rejection, and the counterexample where a
fresh envelope reuses a caller-selected ID after the old row has expired. It
also simulates a bounded HMAC-key rekey batch, process restart with mixed key
versions, resumed batches, and retirement only after no old-key rows remain.
For 36,500 rows, the final run reported 7,434,240 bytes after audit deletion
and compaction with indefinite minimal tombstones (203.7 SQLite bytes per row),
versus 24,576 bytes with zero rows after a 30-day purge. These figures are
illustrative only; they are not Redis, PostgreSQL, WAL, encrypted-volume, or
backup estimates. The rekey test is a local SQLite transaction model, not
provider crash or concurrent-write proof.

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

The [PostgreSQL/Redis crash-cut probe](pg-redis-crash-cut-probe.md) now adds
actual-container evidence for one sticky-latch/Redis-AOF crash cut, same-socket
`WAITAOF`, takeover epoch installation, delayed stale-epoch rejection, and
permit denial. A direct pre-install epoch-1 RESUME made Redis ACTIVE while the
PG latch still denied permits; post-install the same epoch was rejected. This
closes that narrow provider-primitive gap, but does not
qualify the live service protocol, HA, or recovery. The candidate also now
excludes actor and human reason from indefinite tombstone MACs as a privacy
recommendation; its retention and key-rotation policy still awaits acceptance
and review. Persistence review agrees that this removes the reason-derived MAC
privacy concern if registration IDs are opaque and non-personal. It requires a
test where a same-ID retry changes only the audit reason, returns the stored
result without a generation advance, and records the retry's reason separately.
It also flagged mutable container tags in the probe; the script now pins the
exact Redis/PostgreSQL image digests recorded in the report.
The accepted spec now defines reason as per-attempt audit metadata, distinct
from state-changing command identity. The candidate adds a resumable dual-key
re-MAC proposal; crash/restart and backup-lifecycle proof remain open.

## Independent rereview at `239695b`

The architecture grill reviewed the new 06b capability and instance-binding
notes. It found and rejected self-referential artifact/attestation hashes; the
proposal now keeps qualification hashes external and leaves signed-attestation
policy open. It also found the in-process descriptor incompatible with an
unresolved remote-provider option; provider topology now remains explicitly
open, with separate contract shapes required for the two options. These edits
correct proposal defects but do not constitute an accepted 06b contract.

Fresh security review kept S30-06 **BLOCKED**. It confirms the 06b status
descriptor cannot prove stale-handler fencing; the per-instance identity pair
is still a candidate with no Compose/Kubernetes/production provisioning,
isolation, or revocation proof; and the built-in Rabbit administrator fencer
is broader than the required narrow capability. Accepted quarantine controls
(TLS, encrypted storage, TTL and backup deletion, audited-only reads, and size
limits) remain unimplemented. Consumer admission and raw-message reads stay
disabled.

Fresh messaging review found four additional contract/proof gaps:

- Ordering is unspecified across replicas, redelivery, and requeue. Either
  explicitly accept unordered at-least-once processing and test concurrent and
  reordered duplicate operations, or define an ordering key and serialization
  contract.
- Broker connection closure does not establish that an old handler stopped.
  A real barrier test must block handler A, close/requeue, let B claim and
  commit, then resume A and prove A cannot commit, finalize, or ACK.
- Per-instance identity-to-connection binding still needs a production-broker
  prototype covering duplicate names, reconnect, target substitution, and
  ambiguous broker observations.
- The audited quarantine reader and the fence identity must remain separate.
  Tests must prove durable audit before any read/export is returned, direct
  read denial, fencer payload-read denial, and denial of unrelated management
  operations.

These findings keep the feature at **NEEDS_MORE_DESIGN**; no implementation or
design-gate PASS is authorized by the candidate notes. The [disposable
two-user Rabbit binding probe](instance-binding-prototype.md) confirms broker
observation distinguishes two authenticated usernames and two live connection
IDs even when both clients choose the same display name. Closing one observed
ID left the other connection open; after reconnect, the old ID returned 404
and did not close the replacement. This does not test inspect/close races,
connection-ID reuse, authenticated gate-subject mapping, or a least-privilege
fencer. The independent prototype evaluator returned **NEEDS_MORE_EVIDENCE**
for P-003 because the probe does not meet the predeclared identity-mismatch,
active-consumer drain, stale-observation, timeout, or narrow-fencer criteria.
It confirms only broker observation and one non-racy exact-ID close/reconnect
schedule. Production and Kubernetes provisioning evidence and the security/
06b stale-handler gaps remain open.
