# S30-06 gate protocol refinement — 2026-10-05g

Disposition: **NEEDS_MORE_DESIGN**. This candidate refinement does not create a
PASS design gate or authorize production source/deployment work.

Three independent reviewers re-read the current P-001/P-002/P-003 candidate.
The edits close specific wording contradictions, but provider qualification,
numeric performance targets, and accepted ownership contracts remain absent.

## P-001 restore and release protocol

- PostgreSQL `gate_control`, Redis state, Redis command tombstones, command MAC
  inputs, and signed permits consistently carry the restore episode.
- RESUME may durably commit the audited ACTIVE state in Redis and clear the
  PostgreSQL latch while an external restore inhibit still denies admission.
  It then requests release bound to that restore episode and RESUME command.
  Until durable release acknowledgement, status is `ACTIVE_BUT_INHIBITED`,
  readiness remains down, permits are denied, and the command remains
  `ACTIVATION_PENDING`. A same-command status resource reports pending-to-final
  transition. Lost/uncertain release replies are reconciled idempotently.
- Still open: the control-plane owner, authentication/consistency contract,
  anti-rollback if that control itself is restored, episode-history retention,
  UUID-reuse protection, enforcement of every restore path, provider proof,
  and command-status resource authorization/retention.

The independent architecture reviewer found no circular dependency in this
release ordering: RESUME does not require an admission permit. The owner and
provider evidence remain blockers.

## P-002 privacy and deletion

- The Redis event timestamp is now explicitly atomic event-creation time, not
  the later `WAITAOF` acknowledgement. For a qualified timestamp error bound
  `epsilon`, the candidate uses `Redis TIME + epsilon + 365 days` as its
  deletion deadline. This retains each event for at least 365 days and at most
  `365 days + 2*epsilon`.
- Backup/export manifests bind artifact identity/version, parent digests, and
  the earliest contained deadline. Derived copies inherit that deadline, and
  an external monotonic inventory rejects an older valid manifest replay.
- The reviewer verified the arithmetic and consistency. The measured `epsilon`
  and maximum over-retention are not selected or provider-proven. If the accepted
  one-year rule is a strict maximum, this timestamp candidate is not qualified.
  Backup/PITR/replica deletion, monotonic inventory ownership, re-key crash
  recovery, and KMS retirement proof remain open.

## P-003 one-use admission

- Permit HTTP runs outside the local admission lock. A pending nonce and
  request-start monotonic timestamp are registered under lock; after response,
  the instance reacquires the lock and checks current state, incarnation,
  connection, generations, nonce, deadline, and unused `jti` before atomically
  consuming it and incrementing active handlers. PAUSE invalidates pending
  requests under the same lock; a late response cannot start work.
- `iat` is explicitly the not-before claim; fresh restore-episode state is a
  required permit precondition. Broker connection record IDs must be qualified
  as non-reusable across reconnect/failover or admission stays inhibited.
- Expiry only rejects an unconsumed one-use permit. Expiry after handler start
  does not revoke active work. The prior lease-based drain model does not prove
  the current one-use design and must be replaced. The security reviewer found
  no remaining textual contradiction in these additions.
- Still open: runtime local race proof, broker identity/fencing proof, numeric
  throughput/instance/SLO envelope and load results, Keycloak production
  authentication, issuer key rotation, suspend behavior, and performance under
  the per-handler issuance rate.

## Verification

Passed against the revised docs:

- `python3 -m json.tool docs/specs/S30-AMQP-POISON-001/design.json`
- `python3 -m unittest tooling.agent-harness.tests.test_design tooling.agent-harness.tests.test_spec_inventory` (13 tests)
- `python3 tooling/agent-harness/spec_inventory.py --check docs/specs/INVENTORY.md docs/specs`
- `python3 tooling/agent-harness/harness.py validate-all docs/specs`
- `python3 tooling/scripts/check_markdown_links.py docs/specs/S30-AMQP-POISON-001` (34 files)
- `git diff --check`

The test suite prints expected `ERROR` text from negative fixtures; the suite
exits successfully. S30-AMQP-POISON-001 remains document-only and all candidate
protocols remain unapproved.
