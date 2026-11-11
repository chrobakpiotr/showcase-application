# showcase-application - agent handover

Updated 2026-11-11. Read `AGENTS.md` first. Rule "Handover file" in `AGENTS.md`.

## State

- Log hygiene and tooling, 2026-11-11: `ProblemDetailFactory` logs only the SQLState for database failures, because database messages echo values such as customer emails; `org.hibernate.orm.jdbc.error` is at ERROR for the same reason. The explicit `PostgreSQLDialect` is gone. The frontend has `npm run format` and `format:check` (lockfile Prettier, `.prettierrc.json`, `.prettierignore`), and the frontend CI job runs `format:check`.
- Hosted CI at `0b18f3d` passed Playwright, the critical PostgreSQL and RabbitMQ gates and Agentic SDD. This closes the Docker-dependent integration gap of SHOWCASE-FOLLOWUP-001. See the [hosted CI record](../specs/SHOWCASE-FOLLOWUP-001/evidence/hosted-ci-2026-11-11.md).
- Cleanup after the CI repair: Spotless uses Prettier 3.9.6 with `apps/ecommerce/frontend/.prettierrc.json`, matching the lockfile; `spring.jpa.open-in-view` is false; `@Valid` sits on list elements in `Cart`, `Wishlist` and `Order`; the unused `/h2-console/**` security ignore is gone; the demo order uses `demo.buyer@example.com`; an H2 changeset drops the email constraint too, covered by `RepeatCustomerOrderH2IntegrationTest`. `agentic-sdd.yml` artifact retention is 7 days, which changes the protocol fingerprint: re-plan any in-flight Agentic SDD work. The retry-control trust anchor and pinned digests include the new `spotless-prettier.gradle` hash. All workflows passed at `dea9acd` after one Prettier 3.9 diff in `shipment.model.ts`.
- CI and Agentic SDD failed after these pushes. Fixed: Spotless now pins Prettier 3.8.1 (Prettier 2 flattened Angular control flow) and 26 frontend files are reformatted; the repeat-customer test SKU fits the 40-character column; the retry-control trust anchor and its pinned digests include the new manifest and Spotless hashes; Playwright browser install retries apt DNS failures. All workflows passed at `84066c0`. See the [CI repair record](../specs/ORDER-CUSTOMER-EMAIL-001/evidence/ci-repair-2026-11-11.md).

- SHOWCASE-FOLLOWUP-001 implements the four architecture follow-ups. Adapter rules cover all technical adapter roots and the exact web operator-identity exception. k6 uses an operator token, complete order requests, bounded stock and global abort on contract failure. The AMQP consumer defaults off independently of publishing. Architecture and roadmap now include current modules and separate execution from qualification.
- ORDER-CUSTOMER-EMAIL-001: the first real k6 smoke returned HTTP 500 for a second order with the same customer email. Every order owns its CUSTOMER row, but the baseline made `CUSTOMER.EMAIL` unique. A PostgreSQL-only changeset drops `customer_email_key` and keeps `IDX_CUSTOMER_EMAIL`. `RepeatCustomerOrderPostgresIntegrationTest` covers sequential, concurrent and schema cases and is registered in the critical PostgreSQL manifest. Java was not compiled locally; hosted CI is its first build. After the fix the owner's k6 smoke passed: 20/20 iterations, place p95 406 ms, lookup p95 85 ms ([record](../specs/ORDER-CUSTOMER-EMAIL-001/evidence/k6-smoke-2026-11-11.md)). See the [specification](../specs/ORDER-CUSTOMER-EMAIL-001/spec.md).
- Focused module gates passed with JDK 25 and Gradle 9.7.1. k6 v2.3.0 passed against synthetic HTTP transport only. See [verification](../specs/SHOWCASE-FOLLOWUP-001/evidence/verification.md).

- DEMO-004 lets an operator with `ORDER_WRITE` continue from a confirmed cart to
  the existing order form. Cart writes settle across route changes, checkout
  locks cart edits until navigation settles, and a write error refreshes the
  cart before checkout resumes. A valid unresolved order attempt retains its
  payload and key. See the [specification](../specs/DEMO-004/spec.md).
- All 31 Playwright tests passed on `cedfad713dc3ba1ff16e85569e183e873a3786fc`,
  including the cart-origin response-loss scenario. The scenario verified
  replay with the same payload and key, one order in history, and the cart
  still available. [Hosted Playwright job](https://github.com/chrobakpiotr/showcase-application/actions/runs/42624030117/job/129183064988)
- Main pins released Agent Harness `v0.32.0` (`3d742c86abc44227d5b1bfcda1bcf86ca05fcac0`). Both Python gates use the control interpreter. Re-plan any in-flight work under the new protocol fingerprint.
- The exact adoption tree passed 991 harness tests on Linux using the released wheel. The disposable verifier rehearsal reconstructed its plan and refused unqualified execution as expected.
- The owner closed FIRST-RUN-004 and accepted the v0.32.0 migration closeout on 2026-11-08. The live run was not performed because the Linux Codespace had no actively qualified backend. The Linux suite and offline rehearsal are the adoption evidence.
- The owner activated on the Mac on 2026-11-05: `harness.py doctor` all PASS, in a virtual environment at `~/.venvs/showcase-harness`. Install steps: `docs/specs/release-0.31.0/packet.md` in agent-harness.
- The agent sandbox can run `doctor` with a prepared interpreter that has the exact Harness pin. The [tooling guide](../tooling.md#local-harness-diagnostics) records the provisioning boundary and command.
- Earlier FIRST-RUN failures and hosted CI records remain in Git history.

- Owner decisions 2026-11-11: S30-06 is closed by scope revision without implementation; the AMQP consumer stays disabled and any consumer needs a new specification ([decision](../specs/S30-AMQP-POISON-001/evidence/s30-06-closure-decision-2026-11-11.md)). S05-07 is handed to the external handbook owner ([decision](../reviews/external-handbook-handoff-2026-11-11.md)). Provider profile evidence and the required-sandbox live run are closed as not needed.
- Provider re-entry: register no new provider version without structured-output evidence from a real run. The tested profiles stay Claude Code 2.1.289 and Codex CLI 0.160.0. Claude Code access was blocked by the organization (403) on 2026-11-11. A required-sandbox live run needs active backend qualification for its exact checkout and policy and one of these profiles.

## Unfinished

- `qualification/report.py` is the one remaining frozen copy of library code (agent-harness backlog).

## Next step

The backlog has no open items. For frontend formatting failures, apply the Spotless diff from the log; do not format with a different Prettier version.

## Do not touch

- Retain the earlier FIRST-RUN branches and escalated lifecycle state as historical evidence.
- `.agent-state/completion-repair-claims/`: live lifecycle state, unrelated to the old workflow name.
- Files whose digests committed evidence cites, and the proof files listed in agent-harness `docs/specs/AH5-17/name-removal-2026-11-05.md`.
- `docs/agentic-sdd/constitution.md`: its digest is in verification contracts.
- Do not repeat recovery acknowledgement on the Mac checkout.
- Nothing under `.agent-state/` may be removed, including `wayfinder/EPIC-001/map.lock`: the repository lifecycle snapshot records it.
- Do not name a tracked file with `secret`, `credentials` or `.env` in its path: task verification then refuses every candidate.

## Verification

`bash tooling/scripts/run-harness-tests-parallel.sh <dir>` and `python3 tooling/scripts/verify_evidence_checksums.py`, with the pinned library installed. `AGENTS.md` is a protocol file: changing it changes the protocol fingerprint.

On 2026-11-11 the agent workspace had no Docker daemon, no network for the Gradle wrapper and no graphify.
