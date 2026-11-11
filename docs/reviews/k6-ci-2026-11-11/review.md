# Review: k6 order smoke in CI E2E job and frontend format scripts

Disposition: **PASS (static only for failure modes; hosted success run read)**. Formal disposition: **PASS (static only)**.

- Artifact: showcase-application, base 82952c4, commits 9ab655f and b3910f5 (checked-out HEAD 3709ba4 is in the same history; working tree clean). Note: the range 82952c4..b3910f5 also contains 99041bc and 7f49d0d (database-message log scrubbing, Hibernate dialect). They are outside the stated scope and were not reviewed.
- Guidance read from base (AGENTS.md, platform-reviewer.md); the change's own docs edits were treated as claims.
- Independence: INDEPENDENT (fresh context, no implementer overlap). Model/effort that ran: claude-sonnet-5-5 ("Sonnet 5.5"); reasoning effort reported as medium by the requester, not observable by me (unknown).
- Groups loaded: secrets/logs, authentication/session, files/paths, process/file trust, supply chain.

## Verification rows
- V-1 STATIC: diff of ci.yml, capture-operator-token.mjs, package.json, .prettierignore, docs/testing.md.
- V-2 RUN exit=0 (local): `python3 -I tooling/scripts/verify_required_status_policy.py` -> "PASS: required-status policy matches stable aggregate workflow job names".
- V-3 RUN (hosted, log read: run 43101342885, 0b9f15a, job 130793914059 steps 10-11, log fingerprint fc9b322143c745fc): Install k6 OK (`sha256sum -c` "OK"); capture printed only "Operator token captured"; k6 20 iterations, 62/62 checks, http_req_failed 0/42, all thresholds passed; step and job success. `grep -c eyJ` on the whole job log = 0. This establishes only that one run.
- V-4 RUN (hosted: same run, Frontend job 130793925712): "Check formatting" step success; CI quality gate 130798443530 success. Backend job (Spotless gates) success.
- V-5 RUN (local, network): upstream `k6-v2.3.0-checksums.txt` lists 39c3117b...8ab1 for k6-v2.3.0-linux-amd64.tar.gz, equal to the pinned K6_SHA256.

## Answers to the questions

### 1. Token leakage - not confirmed; only Low concerns
- Printing: the script only logs "Operator token captured"; the workflow never echoes the token except in `::add-mask::`. GitHub's default shell is `bash -e` (confirmed in log: `shell: /usr/bin/bash -e {0}`), no `-x`, so the `echo "::add-mask::$ACCESS_TOKEN"` line is not traced. Token is passed to k6 via environment, not argv (not visible in `ps` args). k6 output contains no header/token (V-3, 0 hits for "eyJ").
- Masking timing: add-mask is issued after the token is read and before k6 starts; nothing is printed in between. The runner masks only from that point, but nothing earlier could print it. OK.
- Disk: file created with mode 0600 in `$RUNNER_TEMP` (hosted runner is a single-tenant, per-job VM; adequate) and removed with `rm -f` before masking. Window: capture-operator-token.mjs:36 to the `rm`. `rm -f` is not a secure erase; moot on an ephemeral VM. `writeFileSync` mode applies only on creation (an existing file keeps its mode) - not reachable on a fresh runner. Low.
- Playwright traces/artifacts: the capture script runs its own `chromium.launch()` with no tracing, screenshots or reporter, and writes nothing under `playwright-report/`; the later `playwright-report` upload contains only suite output. Not affected by this change. Pre-existing and out of scope: suite traces (`retain-on-failure`, playwright.config.ts) could contain bearer tokens and the fixture password if a test fails; the stack is disposable and the token short-lived, retention 7 days.
- Credentials: `order-admin`/`password` is hard-coded in the script (capture-operator-token.mjs:23-24), same fixture as e2e/auth.ts for the disposable realm. Note only; confirm it is not a real credential anywhere else.
- Token exposure beyond the job: the job-level `permissions: contents: read`; no `pull_request_target`/fork secrets involved (no secrets used).

### 2. Supply chain - OK
- Version pinned (2.3.0), SHA-256 pinned and checked before extraction (`sha256sum -c -`, `set -e` fails the step on mismatch); hash matches upstream checksums (V-5). Archive layout `k6-v2.3.0-linux-amd64/k6` and PATH via `$GITHUB_PATH` (effective for the next step) confirmed by the hosted run. Pattern is consistent with the kubeconform install at ci.yml:392. Not signature-verified (no cosign); the hash is TOFU against the same host - acceptable and consistent with repo practice. Low (Note): amd64 only, bound to `ubuntu-latest`.

### 3. Step order, determinism, failure modes - sound with Low concerns
- Stock: `setup()` receives ITERATIONS units itself, so leftover Playwright stock does not matter; leftover orders/reservations are on a stack torn down right after.
- Token lifetime: the realm export sets no explicit `accessTokenLifespan` (Keycloak default 5 minutes); the token is captured seconds before use and the run takes ~10-15 s (hosted: 10.4 s). Max 2m30s is within the default. If the realm lifespan is ever shortened below ~3 min the smoke fails closed (setup aborts on non-200). Low.
- Rate limit: `placeOrder` rate limiter (OrderCommandExecutor) is shared; Playwright orders precede the smoke. Hosted run passed 20 orders in ~10 s. I could not find the configured limit values quickly; whether slow-runner timing could trip it is UNVERIFIED (one hosted run only). Low/Medium-flakiness concern; probe: rerun the job several times.
- Failure: k6 nonzero exit (thresholds fail or `exec.test.abort`) fails the step under `bash -e`; the e2e job fails; `ci-quality-gate` requires `E2E_RESULT == success` (verify_ci_quality_gate.py REQUIRED_RESULTS), so the gate fails. "Dump application logs on failure" and teardown (`if: always()`) still run. A capture failure aborts before k6 (no file, nonzero exit) and gives no screenshot/diagnostic; Low.
- Capture logic (mjs:25-34): takes the Authorization header of the first `/home/api/` request after navigating to catalog; header-less requests ignored; empty token throws. Fixed 60 s timeouts. OK.

### 4. Required-status policy - holds
No job or job name added/renamed; steps were added inside the existing `e2e` job, already in `needs` and in `REQUIRED_RESULTS`. V-2 passed; hosted gate success.

### 5. Prettier scripts vs Spotless - match
- Glob `src/**/*.{css,scss,ts,html,js,json}` equals Spotless `target` (css, scss, ts, html, js, json). `.prettierignore` (`src/generated/`, `src/assets/i18n/`) equals `targetExclude`. Both use Prettier 3.9.6 (package.json `^3.9.6`, lockfile) and `.prettierrc.json`.
- Minor difference: the Prettier CLI honors `.editorconfig` and `.gitignore`; Spotless may not. The editorconfig values (2 spaces, final newline) equal Prettier defaults, so no divergence today; hosted Spotless and `format:check` both passed on the same tree (V-4). `^3.9.6` allows a drift from the Spotless-pinned `3.9.6` if the lockfile is regenerated; the docs claim "the version in package-lock.json". Low.

## Findings (ranked)
1. Low - Rate-limit/timing sensitivity of the smoke after the Playwright suite is unproven beyond one hosted run (ci.yml:588-597). Probe: rerun the E2E job 3-5 times or read the `placeOrder` limiter config.
2. Low - Token file briefly on disk and `rm -f` rather than avoiding the file (ci.yml:591-594, capture-operator-token.mjs:36). Acceptable on a hosted VM; could pass via stdout capture into a variable instead (but stdout capture risks logs).
3. Low - No diagnostics when token capture fails (headless login failure) (capture-operator-token.mjs:20-33).
4. Low - Prettier range `^3.9.6` vs Spotless pin `3.9.6` (package.json:42; spotless-prettier.gradle).
5. Note - Hard-coded fixture credentials in the script (mjs:23-24); amd64-only k6; pre-existing Playwright traces may hold tokens (playwright.config.ts).
6. Note - Scope: the range also includes unrelated commits 99041bc and 7f49d0d, not reviewed.

Blocking: none. Required before acceptance: none; optional rerun for finding 1. Implementation acceptance: UNVERIFIED (owner-set).

Falsification coverage: token leakage tested; supply chain tested (hash re-derived against upstream); ordering/idempotency tested statically + one hosted run; failure propagation tested statically + gate script; authorization of the token roles (ORDER_READ/WRITE, INVENTORY_WRITE) shown by hosted success; migration compatibility not relevant; observability of failure covered in finding 3.

## Follow-up by the implementer (2026-11-11)

All findings are Low or Note and accepted as recorded. The k6 smoke passed again in later CI runs on `main`; reruns
will show whether rate-limit or timing sensitivity exists.
