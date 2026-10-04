# S30 master-review re-review — 2026-10-04

Reviewed implementation snapshot: **`f732c75da8cacea5d5d74d94b5c6083669af7a2d`**. Source findings: the user-
provided `showcase-review-agent-plan-2026-09-30.md`, especially sections 5–16.
This new checkpoint supplements that review; it does not rewrite S22 closure,
SDD-OBS-001 task/verification history, or the M5.3 master-closure record.

The verified checkpoints since the supplied review are listed in
[`S30-implementation-progress-2026-09-30.md`](S30-implementation-progress-2026-09-30.md).
Tests named there are focused checkpoint evidence, not a current full-release
CI run. No remote settings or external handbook were changed.

## Finding status

| Finding | Status | Current evidence and remaining acceptance boundary |
|---|---|---|
| F01 — semantic obligations and origin | **Partial / open** | S30-01a preserves applicable profile requirements alongside task occurrences. S30-01b versions obligation/unit identities and planned origins, and v2 execution/completion remain blocked until trusted admission and full coverage exist. Planned labels do not prove actual independent execution. See the S30-01 records in the progress report. |
| F02 — non-cacheable terminal PASS | **Narrow issue addressed; integration open** | S30-02a records successful non-cacheable execution without requiring reusable evidence, retains post-drain drift checks, and runs again on a later attempt. This does not close accepted-plan admission, coverage, or full terminal lifecycle acceptance. |
| F03 — profile command authorization | **Parser gap addressed; production execution open** | S30-04b admits only the three exact reviewed scripts when reconstructed accepted-plan context authorizes them. Fabricated/mutated commands reject. Physical preparation still fails closed with origin admission unavailable; do not claim the critical verifiers ran through the harness. |
| F04 — required-gate applicability | **Addressed at local planner policy** | S30-04a adds truth-table selection for persistence, AMQP, domain/foundation, orchestration, PIT configuration, verifier/manifest and build configuration paths. This does not replace dedicated CI or qualify verifier execution on an isolated host. |
| F05 — production backend qualification | **Open; blocks accepted execution** | S30-03a rejected available native sandbox probes for Q08/Q09 descendant escape; the Codex wrapper probe was unsupported for most checks. S30-03b reports discovery separately from qualification/readiness. Production qualification, lifecycle admission and one-shot launch capability are absent. |
| F06 — candidate/build-output separation | **Primitive implemented; operational integration open** | S30-02b1 materializes and validates a diagnostic source workspace with privacy and path checks. It grants no launch/PASS authority; accepted writable-root policy, read-only Git metadata and producer/consumer output binding remain. Arbitrary builds in a sealed candidate are not yet an accepted operational path. |
| F07 — AMQP permanent-error handling | **Open; policy decisions accepted, implementation blockers remain** | S30-06a established RabbitMQ stop/restart redelivery and positive-confirm-plus-return behavior in a bounded spike. The user accepted deployment tooling as sole quarantine-topology owner, exact names and all-environment scope; provisioning artifacts and production handoff remain unimplemented. The user also accepted operator-only resume after restart; atomic audit/state transitions; per-instance drain confirmation before resume; and encrypted, access-restricted quarantine retention for 30 days. S30-06e removes operation/order identifiers and raw JSON from the listener's normal INFO log; the captured-event test passed 3/3 and received independent security review PASS. Pause/in-flight/readiness/channel-loss mechanics, durable restart guard, quarantine encryption/access/audit/backup/size enforcement, retry taxonomy, instance-membership and handler fencing, malformed identity and database-outage behavior remain open. Expired-instance removal requires operator confirmation that its RabbitMQ consumer connection is fenced or closed; lease expiry alone is insufficient. No production poison policy is claimed. |
| F08 — shipment HTTP/retry contract | **Partial** | S30-07a–c add complete header validation, typed stale/fingerprint conflict codes and reload-persistent operation identity in the client. Focused backend/frontend tests passed, but the packet does not claim the full real-backend response-loss/reload E2E matrix. |
| F09 — bounded dispatch retries and operations | **Core behavior implemented; retention decided, production-scale cost remains open** | S30-08a–c add due-order progress, bounded attempts/parking and validated worker/timeout configuration. S30-08d1–d2 add a bounded parked queue and atomic audited redrive with PostgreSQL concurrency coverage. S30-08e measures synthetic one-million-row distributions with 0.1% and 10% PARKED; work grows with parked-set cardinality, but no production distribution or latency objective is established, so no index/SLA decision follows. The user has decided terminal dispatch rows remain indefinitely because `(order, type)` is the dedup fact; no deletion/retention implementation should be added without revising that contract. |
| F10 — stale/overstated documentation | **Partial** | S30-09a records a read-only ruleset snapshot with exact contexts and `bypass_actors: null`; its branch-protection endpoint was not queried, so administrator bypass state outside that response remains unverified. S30-09b adds current CLI guidance and caveats without restoring the removed handbook or changing the Drive PDF. Automated doc-example smoke and final post-implementation closure remain open. |

S30-05a adds schema field-surface regression coverage to the existing harness
suite. It does not demonstrate the accepted-authority positive path or a
qualified-host execution smoke. S30-10 Recovery Workbench has not started and
remains gated by the missing operational prerequisites identified by the
original review.

## Reopened readiness claims

The SDD-OBS-001 implementation may retain its historical
`implementation-authorized` master decision and historical test/closure records.
Those records do not establish current end-to-end verifier readiness. The
following remain unproven or explicitly blocked: complete independent-origin
admission and obligation coverage; one-shot qualified launch and durable
terminal completion; safe build-output integration; AMQP poison quarantine and
restart fencing; measured production-scale dispatch cost; qualified-host and
accepted-authority positive-path CI; and the Recovery Workbench prerequisites.
The canonical M5.3 Design Gate remains **NOT PASS**.

The 2026-10-04 ruleset artifact records only the response from
`GET /repos/user99987/showcase-application/rulesets/21939086`. Its
`bypass_actors: null` value is not used to infer branch-protection or all
administrator-bypass behavior. No remote mutation was made.

## Supplemental re-review — baseline `46d1415b5385f16ef953f9a275a6c33e3c143b1b`, updates `c34290e`, `b6ff924`, `c4f5301`, `acbb039`, `072c536`, `ab8e41a`, `5f90540`, `6ea57c3`, `85aeb35`, `8efaf19`, `ae8b45f`, `bc8346e`, `d23e837`, `4653b96`, `2b38dd3`, and `e99c324`

This update incorporates the S30-07d/e/f/g evidence recorded after the original
`f732c75` snapshot, the S30-09d documentation smoke, and the S30-08e synthetic
parked-query measurement, Docker's discovery-only doctor status, and the
operator-reference correction. The later `8efaf19` synchronization records the
S30-07g HTTP evidence; `ae8b45f` corrects the earlier commit count, `bc8346e`
records the completed Docker documentation checkpoints, and `d23e837` syncs
the list through those twelve commits. At the `d23e837` snapshot, the branch
was thirteen commits ahead of `origin/main`; the later `4653b96` checkpoint
adds the Docker-only auto/required no-launch regression and records the accepted
AMQP restart/data and dispatch-retention decisions. Independent review passed;
the focused command module passed 8/8 under Python 3.12. At the `4653b96`
snapshot, the branch was fourteen commits ahead of `origin/main`; `2b38dd3`
refreshed this review to include that checkpoint. The subsequent `e99c324`
commit removes sensitive listener identifiers from logs, with the focused AMQP
test and independent security review recorded above. At the `e99c324` snapshot
the branch was sixteen commits ahead of `origin/main`; the header lists all
sixteen. No live GitHub CI result was checked.

| Finding | Updated status | Current evidence and remaining acceptance boundary |
|---|---|---|
| F05 — production backend qualification | **Open; Docker capability evidence is promising but insufficient** | The reported S30-03c disposable Docker Desktop spike passed Q01–Q10 at the container boundary and demonstrated container-wide drainage/fresh-CLI discovery (Q13–Q15). Raw probe output was not retained as a repository artifact. S30-03d now reports the Docker CLI as discovered in `doctor`, while qualification support, qualification and launch readiness remain false. S30-03e proves that Docker-only discovery cannot launch through either `auto` or `required` command mode; the 8/8 focused suite and independent review are negative-path evidence only. Q11 lacks exact canonical policy/repository/birth binding; Q12 has no qualified adapter. Q16 showed that the same stopped container ID can restart; this becomes unsafe if an adapter treats ID alone as one-shot identity without a per-start generation and durable terminal/drained state. No payload is authorized and no production qualification is claimed. |
| F08 — shipment HTTP/retry contract | **Reviewed shipment acceptance paths now have real-boundary replay, conflict, malformed-header, and duplicate-click evidence** | S30-07d proves commit-then-lost-response replay through real HTTP and PostgreSQL: the browser response is aborted after commit, reload shows persisted state, and retry reuses the original operation ID and expected status. S30-07e proves a stale second client receives the typed 409, makes no automatic advance, then advances only after a deliberate action with a fresh key/status pair. S30-07f double-clicks while the first browser request is held before backend forwarding; the advance control is disabled and exactly one request reaches the route, which is then forwarded once to the real backend. S30-07g sends key-only, status-only, blank-key, and 81-character-key requests through authenticated real HTTP; all return 400, and reloading confirms the shipment remains PENDING. Independent review of S30-07f and S30-07g returned PASS. |
| F10 — stale/overstated documentation | **Core corrections and executable smoke addressed; closure remains open** | S30-09a/b correct the required-check snapshot interpretation and current CLI reference; S30-09d executes the safe advisory example in a synthetic repository and checks option/output-shape drift; S30-09e updates F05 wording to match the Docker capability and discovery-only evidence. The ruleset artifact still does not establish branch-protection or every administrator-bypass behavior. The external Drive handbook/PDF was not changed. A final cross-document review after remaining implementation is still required. |

The latest local CI repair is recorded at `46d1415`. It records an expiring
OWASP Dependency-Check accepted-risk exception for the DOMPurify version
bundled in Swagger UI; this makes the scanner gate pass but does not remediate
the vulnerable bundled component. Do not report that dependency as fixed.

Other finding statuses remain as in the original table. In particular, F01–F06
and F07 remain open at their stated production-admission, qualification,
output-boundary, poison-policy, and independent-CI acceptance boundaries. For
F09, indefinite dispatch-row retention is now accepted, while its measured
production-scale cost and latency objective remain open. The
Recovery Workbench remains gated. The canonical M5.3 Design Gate remains
**NOT PASS**.

## S30-06 decision update — 2026-10-04

The user subsequently accepted the exact durable topic quarantine exchange
`com.cp.e.topic.order.quarantine.v1`, durable classic queue
`com.cp.q.order.quarantine.v1`, and binding key `order.quarantine.v1`, with
deployment tooling as the sole topology owner. The topology must exist in all
environments; production Helm continues to use external RabbitMQ and needs a
documented handoff. The accepted encryption policy requires
server-authenticated TLS with CA/hostname verification and separate broker
credentials, plus encrypted host/storage-class volumes in all environments.
Operators may read/export data only through an audited tool; direct AMQP and
management reads remain disabled until it is implemented and tested. The
accepted retention mechanism is Rabbit queue-level
message TTL (`x-message-ttl`), verification that messages are no longer
retrievable one hour after expiry. Each backup/export containing a message
must be deleted by that message's original 30-day deadline, even if a newer
full-broker snapshot has to expire early. Every raw-message read/export must
be audited. Operators may access data only through an audited tool; direct
AMQP and management reads remain disabled until it is implemented and tested.
The user also accepted a 1 MiB combined body-plus-headers limit per message
and 1 GiB per queue; overflow must be rejected, leaving the source unacknowledged
and consumption paused. A source-level audit now maps the candidate permanent
error types to their current listener/service/receipt-adapter throw sites. It
does not authorize broad exception-type classification: boundary tests must
still prove the receipt transaction behavior, while database, commit, and
unclassified failures remain unknown. The user selected a deployment-managed
global pause gate independent of the application database. The user then
accepted a deployment-owned gate service with app PAUSE-only and audited-tool
RESUME-only operations; the app must not have authoritative-store write
credentials. Every authenticated PAUSE and RESUME must atomically update gate
state and append an audit entry with action, validated caller identity, time,
outcome, and state generation; RESUME also records operator reason. Return
success only after Redis confirms the configured fsync threshold. Before
RESUME, every registered live application instance must confirm delivery stop
and active-handler drain. Exact token claims, audit retention and timestamp
format, Redis transaction/idempotency/crash/failover protocol, and instance
liveness lease and fencing/closure evidence remain to be designed. Lease expiry
alone cannot establish that an unresponsive instance stopped; the operator
must confirm its RabbitMQ consumer connection is fenced or closed before
RESUME. The
platform audit found Redis on each local
deployment surface, but all current instances are ephemeral and therefore do
not yet qualify as a durable gate. The user selected a dedicated Redis gate
store with synchronous AOF and encrypted persistent local/dev storage, plus
external production Redis with equivalent durable commits. A tmpfs process-
restart probe returned local fsync via WAITAOF, but does not qualify deployment
storage or failover durability. AOF/WAITAOF errors, insufficient fsync, or
uncertain Redis state must fail closed. The user accepted dedicated app-workload
and individual operator identities, a gate-specific resume permission, and
durable validated-subject audit; exact claims, retention and timestamp format
remain open. Local/dev use the existing Keycloak
with a distinct gate audience/client and role; production configures the
corresponding external issuer/client. Exact identifiers and credential
lifecycle remain open.
These decisions resolve
policy choices only. No deployment artifact, durable pause
guard, operator authorization, quarantine encryption/access audit, or 06b
attempt fencing is implemented or proven by this documentation update. The
data policy still blocks enabling raw-payload quarantine wherever encryption
and access restrictions are not enforced. No live GitHub CI result was
checked.
