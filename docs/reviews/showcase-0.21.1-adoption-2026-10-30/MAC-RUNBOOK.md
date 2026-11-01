# Mac recovery and later local code adoption

Prepared 2026-10-30. The owner has authorized code adoption and live recovery, and will execute on the Mac later. This document performs neither. Remote Showcase code adoption is `d5103ae493ee69d579445be3625222bf7789b2cb` on main; its parent is `7c180f6` (abbreviated supplied integration parent). CI `41018857976` and SDD `41018857987` were pending when this runbook was prepared. Check their final exact-commit results before local adoption. Remote code adoption does not update the Mac or establish any live-state fact.

The critical order is: freeze and preserve the protected old consumer/runtime → inspect the actual live bindings and state → satisfy writer, drain and sandbox-boundary prerequisites → run the frozen acknowledgement with its separate 0.20.0 environment → verify state and markers again → review and perform a separate local code update. Preserve the old environment throughout. No paid/model canary or sandbox experiment is included or implicitly authorized by this runbook. If evidence of the required protection is unavailable, stop at that prerequisite.

## Recovery kit

Use the provided `mac-recovery-kit.tar.gz`; extract it into a private operator-controlled directory outside agent writable roots. Inside the extracted `mac-recovery-kit` directory run `shasum -a 256 -c SHA256SUMS` and stop on any failure. Set `KIT_DIR` to that directory's actual absolute path. The kit contains the unmodified frozen tool, historical tests and README/packet, retained 0.20.0 wheel, and this runbook. It contains no live state or registry. The archive's published SHA-256 should also be checked before extraction. Frozen documents' old “no live authorization” statements describe their publication-time boundary; the owner's later explicit order supplies authorization, while every technical prerequisite still applies.

## 1. Freeze and record the actual Mac before changing code

Stop scheduling new agent, lifecycle, verification, Wayfinder and control-plane operations, including background launchers and related worktrees. Ask existing owners to finish or cancel through their normal recorded cancellation/drain protocol. Do not steal fresh leases, kill by guessed PID, delete journals, or infer drain merely from one process scan or lease expiry. Record all active executions, process birth identities, child processes, reservations, verification ownership and leases; require terminal/drained proof for each. There is no universal safe drain command in the frozen acknowledgement CLI.

Choose these real absolute paths on the Mac; examples are deliberately not fabricated. Use a private evidence directory outside the repository/state root. These commands assume Bash and stop if required values are unset.

```bash
set -euo pipefail
: "${LIVE_REPO:?set the actual authoritative Mac checkout absolute path}"
: "${EVIDENCE_DIR:?set a new private evidence directory outside LIVE_REPO}"
: "${OLD_CODE:?set a new absolute detached-worktree location outside LIVE_REPO}"
: "${RECOVERY_VENV:?set a new absolute dedicated 0.20.0 venv location}"
: "${PY313:?set an absolute Python >=3.13 interpreter path}"
: "${KIT_DIR:?set the actual absolute checked extracted mac-recovery-kit directory}"
WHEEL020="$KIT_DIR/wheels/agent_harness-0.20.0-py3-none-any.whl"
TOOL="$KIT_DIR/tools/snapshot_acknowledgement.py"
(cd "$KIT_DIR" && shasum -a 256 -c SHA256SUMS)
umask 077
mkdir "$EVIDENCE_DIR"
cd "$LIVE_REPO"
test "$(pwd -P)" = "$(git rev-parse --show-toplevel)"
git rev-parse HEAD > "$EVIDENCE_DIR/live-head-before.txt"
git rev-parse --path-format=absolute --git-common-dir > "$EVIDENCE_DIR/git-common-dir.txt"
git worktree list --porcelain > "$EVIDENCE_DIR/worktrees.txt"
git status --porcelain=v1 --untracked-files=all > "$EVIDENCE_DIR/worktree-status-before.txt"
git diff --binary > "$EVIDENCE_DIR/tracked-unstaged-before.patch"
git diff --cached --binary > "$EVIDENCE_DIR/tracked-staged-before.patch"
git for-each-ref --format='%(refname) %(objectname) %(symref)' > "$EVIDENCE_DIR/all-refs-before.txt"
ps -axo pid=,ppid=,pgid=,lstart=,stat=,command= > "$EVIDENCE_DIR/processes-before.txt"
```

Capture authoritative `.agent-state`, `.agent-runs`, protected host registry, verification journals, relevant external worktree run artifacts and local telemetry to a private backup before any checkout change, preserving permissions, symlinks and metadata. Capture the common Git directory (including lifecycle refs and objects) using the host's reviewed backup procedure while writers remain frozen. Keep backups private and outside agent-writable roots. Do not follow unknown symlinks or publish secrets. The process listing and Git patch files are evidence, not a complete backup; ignored/untracked telemetry and external run directories must be preserved separately. Do not run `git clean`, `reset --hard`, stash-and-drop, blanket `pull`, or replace the whole wrapper directory.

Confirm that the current protected consumer implementation matches the reviewed pre-adoption consumer `e5b54adaf7ea4a38e4626f5750da6561db41d688` at the relevant paths. An unexpected live code delta stops this recipe for review; a remote SHA is not evidence that the Mac matches it. Preserve actual local changes and telemetry first.

## 2. Preserve the protected old code and separate 0.20.0 runtime first

The rollback source is the consumer-patched Showcase tree at `e5b54adaf7ea4a38e4626f5750da6561db41d688`, including marker-aware shim, registry companion, runner and writer/retry protections. A generic historical Harness wrapper is not this source. Preserve the existing Mac 0.20.0 venv at its original absolute path; do not upgrade it in place or assume a relocated venv remains executable.

Before updating the live checkout, create a detached code worktree at the reviewed old consumer commit, after confirming that object is present and trusted:

```bash
cd "$LIVE_REPO"
git cat-file -e e5b54adaf7ea4a38e4626f5750da6561db41d688^{commit}
git worktree add --detach "$OLD_CODE" e5b54adaf7ea4a38e4626f5750da6561db41d688
git -C "$OLD_CODE" rev-parse HEAD > "$EVIDENCE_DIR/preserved-code-head.txt"
git -C "$OLD_CODE" rev-parse --path-format=absolute --git-common-dir > "$EVIDENCE_DIR/preserved-code-common-dir.txt"
```

This creates no branch. It changes Git worktree metadata, not lifecycle refs. The detached worktree shares the real common directory and is a preserved code reference, not a recovered state root. Never run the acknowledgement against `OLD_CODE`; its `--repository` must remain `LIVE_REPO`. Do not create a fresh clone and assume the registry binding follows it: the host registry hashes the resolved Git common-directory identity.

Build a separate recovery environment from the reviewed retained wheel. This is independent of the future 0.21.1 consumer environment. The exact retained wheel used in the isolated review hashes to `771263854ba2cc5964c9026d236b2b8e0dd85a3fbdc59aafbd38873ecc1bcd81`; if transferring that artifact, verify this hash. The tool file hashes to `a39669688ba73cbdc021d6529cfb0eb8839be9b8cff87e3af612f5e273890c6f`.

```bash
shasum -a 256 "$WHEEL020" "$TOOL"
"$PY313" -m venv "$RECOVERY_VENV"
P20="$RECOVERY_VENV/bin/python"
"$P20" -m pip install --no-index --no-deps "$WHEEL020"
env -u PYTHONPATH -u PYTHONHOME "$P20" -I - "$TOOL" "$WHEEL020" <<'PY'
import hashlib, importlib.metadata, importlib.util, pathlib, sys
import agent_harness
from agent_harness import state_snapshots as ss
assert sys.version_info >= (3, 13)
assert agent_harness.__version__ == importlib.metadata.version('agent-harness') == '0.20.0'
assert 'site-packages' in pathlib.Path(ss.__file__).parts
assert hashlib.sha256(pathlib.Path(ss.__file__).read_bytes()).hexdigest() == '793c8b9d95460af3eb46319e630352eda51d8a62011e580298db857389c0f12f'
assert hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest() == 'a39669688ba73cbdc021d6529cfb0eb8839be9b8cff87e3af612f5e273890c6f'
assert hashlib.sha256(pathlib.Path(sys.argv[2]).read_bytes()).hexdigest() == '771263854ba2cc5964c9026d236b2b8e0dd85a3fbdc59aafbd38873ecc1bcd81'
spec = importlib.util.spec_from_file_location('ack', sys.argv[1]); ack = importlib.util.module_from_spec(spec); spec.loader.exec_module(ack)
ack.require_runtime()
print(sys.version, agent_harness.__version__, ss.__file__)
PY
env -u PYTHONPATH -u PYTHONHOME "$P20" -I "$TOOL" --help
```

The digest is the installed library implementation, not the tiny consumer import wrapper. The tool refuses 0.21.1 and offers no version/digest override. A fresh installation failure is a stop condition. The optional reviewed historical fixture gate is `bash <tools-directory>/test-snapshot-acknowledgement.sh "$P20"`; it runs isolated temporary repositories and is not live-host qualification. Its `--help` flags were checked against the reviewed 0.20.0 environment during preparation of this runbook.

## 3. Re-establish all live entry conditions

Keep all writers and launchers frozen through the operation and the later local code cutover. Do not invoke Wayfinder status/frontier as passive inspection: those paths can clean claims. Use direct read-only inventory and the reviewed registry loader. Require:

- Actual root, linked worktrees and common Git directory are identified together; protected host registry directory is owner-owned, non-symlink, mode 0700, and its regular single-link registry file is owner-owned mode 0600 with the correct common-directory binding. Required unresolved entries `SDD-001` and `AH5-04B-QUAL-001` remain present. Preserve any additional unresolved entries. Never recreate the registry to make a binding check pass.
- Both unresolved features still report marker-driven `recovery-required`/unknown lifecycle state; malformed, missing or unreadable registry conditions must not be repaired by deleting the registry. The acknowledgement does not reconstruct either feature's history or remove markers.
- Deployed Wayfinder and control-plane writers use the reviewed shared lifecycle exclusion and map-lock ordering, including claims, cleanup and bootstrap. Source hashes and the isolated writer tests are supporting evidence, not proof of a changed live deployment.
- All active model/process families and verifications are drained, no fresh owners/leases remain, no background writer can restart, and no process birth-identity ambiguity remains. Missing process/ownership evidence stops work.
- The audit path `$LIVE_REPO/.agent-runs/state-acknowledgements` is outside `.agent-state`, non-symlink, owned by the operator, mode 0700. Its parent chain is safe. Every enabled agent sandbox is proven unable to write it and the protected registry. Same-user mode 0700 alone is not a sandbox boundary. Historical Codex evidence does not qualify a new host; Claude protection remains unverified without actual relevant evidence. This runbook does not authorize a paid model/sandbox test to fill that gap. If the live denial boundary cannot be established from valid evidence, do not invoke the tool.

Read-only registry inspection (no environment override or registry write):

```bash
env -u PYTHONPATH -u PYTHONHOME "$P20" -I - "$LIVE_REPO" <<'PY'
import pathlib, sys
repo = pathlib.Path(sys.argv[1]).resolve(strict=True)
sys.path.insert(0, str(repo / 'tooling/agent-harness'))
import unknown_state_registry as u
registered = u.load_registry(repo)
assert registered is not None, 'protected host registry missing; stop'
assert u.UNKNOWN_STATE_FEATURES.issubset(registered)
print('registry:', u.registry_path(repo))
print('unresolved:', sorted(registered))
PY
```

Do not run arbitrary lifecycle mutation commands as a marker test on the live state. The earlier isolated consumer focused result was **37 tests PASS**, not a full consumer suite and not proof of live-host readiness.

## 4. Inspect authoritative snapshots and obtain the real manifest ID

The frozen CLI fixes both old refs to `d41f0aa9e6b8d6f5d011024bb5c29ac647f7c9cd`, feature `API-ACCESS-001`, and only the extra empty regular `wayfinder/EPIC-001/map.lock`, mode 0644. Its SHA-256 is `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`. These are incident expectations, not fresh observations. Stop if the live facts have moved. Do not change tool constants, use its private fixture seam, reset refs, or invent an expected snapshot ID.

With writers still frozen, the following uses reviewed read-only helpers and prints the ID from the validated authoritative manifest. It does not execute `recover` or acquire/create state locks:

```bash
env -u PYTHONPATH -u PYTHONHOME "$P20" -I - "$TOOL" "$LIVE_REPO" > "$EVIDENCE_DIR/snapshot-preflight.txt" <<'PY'
import importlib.util, json, pathlib, sys
spec = importlib.util.spec_from_file_location('ack', sys.argv[1]); ack = importlib.util.module_from_spec(spec); spec.loader.exec_module(ack)
ack.require_runtime(); repo = pathlib.Path(sys.argv[2]).resolve(strict=True)
refs = ack.refs(repo)
assert not any(r.startswith(('refs/agent-state-prepared/', 'refs/replace/')) for r in refs)
assert refs.get(ack.CURRENT) == refs.get(ack.FEATURE_REF) == ack.OLD_COMMIT
assert any(r.startswith(ack.ARCHIVES) and oid == ack.OLD_COMMIT for r, oid in refs.items()), 'old archive missing'
assert sum(r.startswith(ack.ARCHIVES) for r in refs) < ack.ss.SNAPSHOT_LIMIT, 'retention would prune'
manifest, old = ack.snapshot(repo, ack.OLD_COMMIT)
assert manifest['feature'] == ack.FEATURE
assert ack.ADDITION['path'] not in old
assert ack.inventory(repo / '.agent-state') == {**old, ack.ADDITION['path']: (b'', 0o644)}, 'state inventory changed'
assert not any('/claims/' in '/' + p for p in old), 'claims present; stop'
print(json.dumps({'old_commit': ack.OLD_COMMIT, 'expected_snapshot_id': manifest['snapshot_id'], 'manifest': manifest, 'addition_identity': ack.identity(repo / '.agent-state' / ack.ADDITION['path']), 'all_refs': refs}, sort_keys=True, indent=2))
PY
```

Independently review the full manifest/state inventory, canonical `API-ACCESS-001` bytes and retention facts against the incident record. Inspect directory metadata and symlinks as well as files. The tool will repeat its checks under its one lifecycle lock; this preceding read-only inspection is not a replacement for the freeze or locked revalidation. A changed historical manifest or extra path requires reconciliation, not acknowledgement of more files.

## 5. Single explicitly bounded acknowledgement

Only after every preceding condition is established, assign `EXPECTED_SNAPSHOT_ID` from the validated manifest printed above, and choose exact operator provenance and reason. Preserve the exact strings for any later audited retry; changing them changes operation identity.

```bash
: "${EXPECTED_SNAPSHOT_ID:?copy the real validated sha256:... manifest ID; never invent it}"
: "${OPERATOR_LABEL:?set the actual operator provenance label}"
: "${ACK_REASON:?set the reviewed incident-specific acknowledgement reason}"
env -u PYTHONPATH -u PYTHONHOME "$P20" -I "$TOOL" \
  --repository "$LIVE_REPO" \
  --expected-snapshot-id "$EXPECTED_SNAPSHOT_ID" \
  --operator "$OPERATOR_LABEL" \
  --reason "$ACK_REASON" \
  > "$EVIDENCE_DIR/acknowledgement.stdout.json" \
  2> "$EVIDENCE_DIR/acknowledgement.stderr.txt"
```

These are the actual four required CLI flags; there is no dry-run, force, snapshot-commit, feature, path or digest override. The tool takes only the lifecycle lock, once, and does not touch/create/replace `map.lock`. It atomically creates one absent API archive ref and CAS-updates exactly feature/current; it writes immutable external audit records and no canonical state file. Preserve old snapshot archives and state bytes, modes, inodes, revisions, epochs and generations.

Refusal, interruption, timeout, lost output or an intent without a result is not success. Never retry automatically, remove audit records, republish old snapshots, prune, reset refs or perform restore/discard as a workaround. Preserve full before/after refs and audit. Inspect whether CAS occurred; only an explicitly reconciled exact-input retry may use the tool's audited idempotency path. A post-CAS failure or later legitimate ref movement requires reconciliation without rollback/republishing.

## 6. Verify recovery, then separately update Mac code

Keep Wayfinder, control-plane and all writers disabled. Require tool exit 0, `applied`/validated `already-applied`, matching immutable intent/result, `postcheck: consistent`, a fresh exact all-ref comparison with only the three declared writes, old archive retained, and unchanged state inventory/lock identity. Both unknown-state features remain blocked. Re-read using the pinned library, not a hand-edited ref:

```bash
env -u PYTHONPATH -u PYTHONHOME "$P20" -I - "$LIVE_REPO" <<'PY'
import pathlib, sys
from agent_harness import state_snapshots as ss
repo = pathlib.Path(sys.argv[1]).resolve(strict=True)
assert ss.current_repository_state_status(repo, repo / '.agent-state') == 'consistent'
snapshot = ss.read_current_repository_snapshot(repo)
print(snapshot['commit'], snapshot['manifest']['snapshot_id'])
PY
git -C "$LIVE_REPO" for-each-ref --format='%(refname) %(objectname) %(symref)' > "$EVIDENCE_DIR/all-refs-after.txt"
```

This snapshot-level acknowledgement does not restore missing task history, grant retries or authorize new lifecycle epochs for unresolved features. No marker/registry repair is part of the operation.

For later Mac code adoption, first verify exact-commit remote checks and inspect the complete live-to-target diff, worktree changes and preserved telemetry. Fetching objects is separate from updating the checkout:

```bash
git -C "$LIVE_REPO" fetch origin main
git -C "$LIVE_REPO" cat-file -e d5103ae493ee69d579445be3625222bf7789b2cb^{commit}
git -C "$LIVE_REPO" diff --stat HEAD d5103ae493ee69d579445be3625222bf7789b2cb
git -C "$LIVE_REPO" diff HEAD d5103ae493ee69d579445be3625222bf7789b2cb -- tooling/agent-harness
```

Do not merge while relevant tracked local changes are unresolved or if the full destination diff contains an unreviewed change. Preserve local telemetry rather than overwriting it. After reconciling code-only changes, require a clean applicable tracked surface and fast-forward ancestry before `git -C "$LIVE_REPO" merge --ff-only d5103ae493ee69d579445be3625222bf7789b2cb`. Never force a checkout/reset if this is blocked. Keep the preserved old consumer checkout and 0.20.0 recovery venv intact.

Install the consumer 0.21.1 requirements into a **different** Python >=3.13 environment, verifying its installed version and exact library pin `ebf9a704791f5f1a583afeb577556326e30ae46b`. Recheck the protected registry, exact retained runner/writer/retry files, both unknown-feature status reports, state consistency, all refs and drained ownership before enabling anything. Do not copy the generic wrapper directory or use the marker-blind 0.21.0 shim as rollback.

Normal Wayfinder/control-plane use remains blocked until the separately scoped relocation of operational data outside `.agent-state` is complete and reviewed. The snapshot acknowledgement and code adoption do not perform that relocation. Code rollback, if needed, is the exact protected old consumer + 0.20.0 pin together; it does not authorize state/ref/marker rollback.

## Sources and verification scope

Reviewed frozen source: `docs/specs/showcase-state-recovery-2026-10-26/tools/README.md`, `snapshot_acknowledgement.py`, and packet sections 8–13 (superseding historical section 3); preconditions report; published 0.21.1 release packet; exact seven-file consumer adoption manifest. Recovery `--help` was executed with the reviewed installed 0.20.0 runtime here. The embedded read-only helper calls and CLI flags were checked against that exact source. No Mac command, live acknowledgement, host sandbox proof or live code update has been executed here. Snapshot IDs and Mac paths must come from the actual host.
