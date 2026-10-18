# P-007 preflight report

Date: 2026-10-18

Result: **BLOCKED before runtime — preflight analysis complete; P-007 closed**

Disposition: S30-06 implementation **DEFERRED**; qualification **NOT
QUALIFIED**; design gate **OPEN**. This disposition does not change accepted
requirements or turn any NOT RUN item into a pass.

Source checkout: `49ab97f580ad21c67e9378e15448aca9fe365c45` plus uncommitted
local research artifacts.

Question: [`../../question.json`](../../question.json)

## Fixed stopping condition

P-007 permits a runtime attempt only if the existing Candidate C restore
wrapper and an existing stale-episode start guard can be identified. If either
is missing, the run stops before starting any container. No new controller,
service, daemon, privilege proxy, or VM may be built for this attempt.

## Preflight observations

- Repository search found the Candidate C model
  `docs/specs/S30-AMQP-POISON-001/evidence/p004_restore_barrier.py` and design
  documents, but no executable restore wrapper or start-control implementation.
- The proposed host record directory
  `/Users/Shared/showcase-s30-control/` and
  `restore-control.json` do not exist.
- `command -v showcase-s30-restore-wrapper` and
  `command -v s30-restore-wrapper` found no command.
- Read-only `docker ps --no-trunc --format '{{.ID}} {{.Names}} {{.Image}} {{.Status}}'`
  succeeded and returned no running containers. Therefore this shell identity
  can access the Docker daemon. A user with this access can invoke Docker
  directly, including explicit `docker start`, `docker run`, or `docker compose
  up`; there is no evidence that a wrapper can prevent such a bypass.
- The prior P-004 restart-policy probe is limited: `restart: no` prevented
  automatic restart in that probe, but does not prevent explicit Compose/Docker
  start or another host supervisor. The existing E2E Compose file is not an
  S30 Candidate C target and has no S30 gate issuer.

These observations establish neither a wrapper nor an exclusive controller.
Since both are prerequisites, no stale-episode restart attempt was made and no
container was started.

## Separate results

| Evidence item | Result | Evidence/limit |
|---|---|---|
| Process termination | NOT RUN | No workload container was created. |
| Refusal to restart an old episode | BLOCKED / NOT RUN | No existing wrapper or stale-episode launch guard was found. `restart: no` is not that guard. |
| Host-user/Docker bypass | OBSERVED AVAILABLE | This shell can access the Docker daemon; direct Docker operations bypass an absent wrapper. No exclusive controller claim is made. |
| Rabbit connection/channel/consumer drain | NOT RUN | No broker or consumer was started. |
| Unacknowledged delivery and redelivery | NOT RUN | No synthetic delivery was created. |
| Stale-generation database CAS refusal | NOT RUN | No handler or database scenario was started. |
| Reuse of old Rabbit credentials outside stopped container | NOT RUN | No credentials were read or used; the negative criterion remains fixed: a successful connection means revocation is UNSATISFIED. |
| Cleanup | NOT REQUIRED | No run-specific runtime resources were created. No global cleanup/prune was run. |

## Recommendation

Defer S30-06. The requested experiment cannot safely proceed on the existing
environment under the no-new-controller/service constraint: there is no
existing wrapper/start guard, and the current Docker-access identity can
bypass a wrapper. `restart: no` alone cannot block stale-episode starts. Do
not reinterpret process termination as broker drain, credential revocation,
database CAS evidence, or protection from already-issued external effects.

The consumer remains disabled. No accepted specification, Harness code,
FileVault setting, purchase, remote, or unrelated Docker resource was changed.
