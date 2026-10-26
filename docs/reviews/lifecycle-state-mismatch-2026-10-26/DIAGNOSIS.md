# Showcase lifecycle state mismatch — read-only diagnosis

Date: 2026-10-26. Repository checkpoint: `5394efc17bedf8cf55f4a938924684685b252dde`.
The inspection used the active Showcase shim and the exact pinned Harness library 0.20.0 at
`82d8b08c744346724e5edac1343bbb0c14c7f274`. No lifecycle state, snapshot, lock, gate, or evidence was
modified. No recovery command was run.

## Binding and control paths

For all three features, the active binding resolves the repository root to the inspected checkout
(`$CHECKOUT`), Git common directory to `$CHECKOUT/.git`, and runtime state root to
`$CHECKOUT/.agent-state`. The lifecycle lock is `.git/agent-state-lifecycle.lock`; the repository
verification lock is `.agent-runs/control/verification-v2/lock`. Both existed as regular files and
were acquired for the read-only inspection.

| Feature | Canonical state path | Historical main-checkout path | Current per-feature ref |
| --- | --- | --- | --- |
| `SDD-001` | `.agent-state/SDD-001-f6b35fe4b93daa844e43.json` — absent | `.agent-state/SDD-001-367b903b22a2b2d09da2.json` — absent | `refs/agent-state/SDD-001` — absent |
| `AH5-04B-QUAL-001` | `.agent-state/AH5-04B-QUAL-001-78d1b1144c2d9d80a74d.json` — absent | `.agent-state/AH5-04B-QUAL-001-c4b0109a14a66accb4e6.json` — absent | `refs/agent-state/AH5-04B-QUAL-001` — absent |
| `SDD-OBS-001` | `.agent-state/SDD-OBS-001-3f8ff1257c272596a44e.json` — present | `.agent-state/SDD-OBS-001-c33b198360b406accab4.json` — absent | `refs/agent-state/SDD-OBS-001` — present |

These locations are computed through `feature_repo_base`, `git_common_dir`, `runtime_state_dir`,
`state_path`, and `main_legacy_state_path` in the active consumer binding. No alternate worktree-local
state root was used.

## Exact reason for `state-mismatch`

Harness 0.20.0's `state_snapshot_status` first calls `current_repository_state_status`, which compares
the complete recursive `.agent-state` file inventory, bytes, and modes against
`refs/agent-state/current`. It returns the repository-level mismatch before checking the
feature-specific snapshot. Thus the same repository-tree discrepancy causes all three features to
report `state-mismatch`.

The current repository snapshot is:

- ref: `refs/agent-state/current`;
- commit: `d41f0aa9e6b8d6f5d011024bb5c29ac647f7c9cd`;
- snapshot ID: `sha256:a41561484b8bc99dc14718468a93f549f27d0ee03134587083984df122279f91`;
- manifest creation time: `2026-10-18T20:13:12.181088+00:00`;
- manifest inventory: 61 files.

The current `.agent-state` tree has 62 regular files. Every manifest entry matches its current bytes,
SHA-256, and mode; there are no missing or changed entries. The only extra path is
`.agent-state/wayfinder/EPIC-001/map.lock`. It is a regular, empty file, mode `0644`, mtime
`2026-10-20T12:44:25+02:00`. The current Showcase-owned `tooling/agent-harness/wayfinder.py`
implements `locked_map()` by opening this path with `a+` under `state_root(epic)`. Its location is
therefore inside the recursively snapshotted `.agent-state` tree. The filesystem metadata and source
identify the artifact class and code path, but do not identify the invocation that created this file.
It has not been removed or rewritten.

There are zero prepared snapshot refs. The map lock was also acquired non-blocking through a
read-only descriptor, then released; no Wayfinder process was present, and the file's inode, size, and
mtime were unchanged. The library's global snapshot inventory is otherwise an exact match. This is a
concrete repository-level snapshot inventory mismatch, not evidence that any of the three feature
state files themselves changed.

## Per-feature state and active-operation evidence

`SDD-OBS-001` has feature snapshot commit
`3ee61db4250951f3eeeed45d5cc38632f8aa6adb`, snapshot ID
`sha256:9fef3f025e24145b62f9f6dccc54ff6763288fa052b2b6bf53d26e640bb671f8`, and recorded state
revision `97cd33bafdeb18d1d7756f632aef936696c3e20ccd9d1eb0a50c7b2e5b881a37`. The canonical file is
byte-identical to the file in that per-feature snapshot. That snapshot contains 11 completed tasks,
zero running tasks, and no task lease fields. It contains no `verification_authority` object. These
facts describe the bytes bound to the feature ref; the failed repository-level integrity check still
blocks normal lifecycle authority.

For `SDD-001` and `AH5-04B-QUAL-001`, there is no canonical or historical main-checkout state file and
no current per-feature snapshot ref. There is therefore no lifecycle record from which to establish
attempt identity or lease status. This absence does not prove that no earlier attempt ever existed.

### Search for missing history

The read-only search covered the active 0.20.0 binding paths above, all currently registered
worktrees, `refs/agent-state/*`, `refs/agent-state-snapshots/*`, Git reflogs/log files for those refs,
Git history for the exact canonical and historical state filenames, feature-scoped run/completion
capture directories, and the features' checked-in evidence directories. The 0.20.0 worktrees for
`AH5-04B-QUAL-001` resolve to the same `.git` common directory; none contains a separate local
`.agent-state` tree. The searches found no current or historical state ref/file or reflog entry for
either feature, and no matching feature-specific `.agent-runs` or completion-capture directory.

The evidence is not equivalent for the two features:

- `SDD-001`: the checked-in evidence directory has no files. Generic baseline/evaluation logs include
  the feature's task listing, but do not establish a lifecycle start, attempt identity, or lease. The
  searched sources therefore do not establish whether a lifecycle attempt started.
- `AH5-04B-QUAL-001`: Git history contains task checkpoint commits, a failed-attempt commit, an
  orchestration-refresh commit, and preserved qualification evidence, including references to
  completion-capture paths. This establishes that work and task-level execution activity occurred;
  it does not recover the missing authoritative lifecycle record, exact current attempt identities,
  or lease ownership. The historical execution trail is therefore partial, not absent.

For both features the correct finding is “authoritative lifecycle history not found in the searched
sources,” not “the feature never started.” Specifications, task declarations, completion text, and
task checkpoint commits cannot be used to synthesize missing lifecycle state.

Under the existing verification-store lock, the 0.20.0 `VerificationStore._scan_executions()` found
zero execution records and zero open/uncertain execution records. No Harness, runner, or orchestrator
process for this checkout was present during the check. These checks do not substitute for missing
lifecycle state for the two features.

## Telemetry diff relationship

The repository snapshot checker recursively inventories only `.agent-state`; the uncommitted changes
to `docs/specs/SDD-OBS-001/spec.md` and `plan.md` are outside that tree and did not cause the
repository-level `state-mismatch`. The extra `wayfinder/.../map.lock` fully accounts for the single
unexpected file.

The telemetry documentation diff does affect the separate feature fingerprint. The persisted
`SDD-OBS-001` state fingerprint is `1aa4a8d7b27b21ae70e052da269fab175be87f33595599f45d53713c50c845c3`,
which matches the fingerprint recomputed from committed `HEAD` inputs. The current working-tree
fingerprint is `d001b2ce41eba5ea0ef7801703fa5c19deef1c171693f189e38b70f609ac61f9`. The changed
`spec.md` and `plan.md` are part of `feature_fingerprint`; consequently the current feature input
binding differs independently of the snapshot inventory problem. This is a freshness/gate concern,
not the cause of the global tree mismatch. The telemetry diff and stale gate remain untouched.

## Recovery paths available in the pinned 0.20.0

- `restore-state` restores the exact current repository snapshot only when the lifecycle state root is
  absent. `.agent-state` currently exists, so this path is not applicable and its target-exists guard
  must not be bypassed.
- `recover-state` creates a new empty lifecycle epoch only after an accepted state-loss incident and
  its evidence baseline are verified. `SDD-OBS-001` already has canonical state, so this operation
  refuses to overwrite it. The other two features have no current recovery-incident record. A new
  empty epoch would not preserve their unknown history and is not authorized by this diagnosis.
- `reconcile-feature` requires an existing valid lifecycle state and enters the same lifecycle lock;
  the repository-level mismatch causes that lock to refuse before mutation. On success it increments
  generation, preserves task history, and invalidates verification authority. It is not a snapshot
  repair and must not be used to clear this mismatch or to make the telemetry gate green.
- `discard-prepared-snapshot` is only for a verified unacknowledged prepared write. No prepared refs
  exist.
- Directly invoking snapshot writers, manually changing Git refs, deleting `map.lock`, copying
  snapshot files by hand, or initializing empty state are not supported recovery procedures.

**The pinned release has no supported, lossless operation for this exact condition:** one persistent,
empty Wayfinder lock file exists in the state root but is absent from the current global snapshot. The
ordinary lifecycle writer cannot be entered because the integrity check refuses first. This is the
specific recovery gap; it is not classified as F1-R1 without further evidence.

## Proposed controlled resolution (not authorized to execute)

1. Keep the 0.21.0 cutover stopped and preserve the current refs, manifest, file inventory, hashes,
   modes, and the exact bytes of existing state and evidence. Do not copy snapshot contents into the
   live root or remove/rename any lock or history.
2. Harness supplies and independently reviews a narrow snapshot-only reconciliation operation for
   repository-snapshot inventory. It must bind to the exact expected old ref and commit and exact
   manifest/inventory. The only currently admissible delta is
   `.agent-state/wayfinder/EPIC-001/map.lock`, after checking the exact path, regular-file type,
   non-symlink status, mode `0644`, and zero-byte size. Any additional, missing, changed, or differently
   typed entry refuses. The operation must preserve every existing `.agent-state` file, feature ref,
   canonical lifecycle record, attempt, lease, fingerprint, and contract; retain the old snapshot;
   record an auditable reason/result; and publish the new current snapshot ref with compare-and-swap
   against the inspected old ref. An interrupted operation may leave only an identifiable prepared
   object/ref; retry must either safely resume the same expected transaction or refuse, never overwrite
   a concurrent publication.

   Wayfinder's `locked_map()` uses `flock(LOCK_EX)` on this exact `map.lock` inode from after its
   second map read through `atomic_json()` publication. It does not acquire the lifecycle lock. The
   snapshot operation must acquire the existing map lock without unlinking, replacing, or renaming it,
   then acquire the lifecycle lock, re-read and validate the expected snapshot ref and complete
   inventory while holding both, and retain both through snapshot publication/CAS. Current lifecycle
   writers take the lifecycle lock and do not acquire the Wayfinder map lock; current Wayfinder writers
   take the map lock and do not acquire the lifecycle lock, so this proposed map-lock-then-lifecycle-
   lock order has no reverse acquisition in the inspected 0.20.0 paths. Harness must confirm this
   against all relevant callers before implementation. A process scan is not a synchronization
   primitive. Replacing/unlinking `map.lock` would split flock coordination across inodes and is
   forbidden. The existing 0.20.0 release has no such operation; no command is executable today.
3. Harness reviews and verifies that operation against an isolated fixture containing this exact
   extra-lock-file case, changed/missing file refusals, an occupied map lock, and a changed expected
   ref. It confirms byte-for-byte preservation and rollback behavior before any live use.
4. Only after separate owner authorization, run the supported command once against the inspected
   expected ref. Immediately re-run the read-only inventory and all three feature checks. Any changed
   ref, any additional difference, or any non-consistent result stops the procedure.
5. Keep lifecycle operations for `SDD-001` and `AH5-04B-QUAL-001` fail-closed until their authoritative
   attempt/lease history is established from supported evidence or an owner approves a specific
   recovery disposition. Do not infer they never started and do not synthesize state. This per-feature
   refusal is distinct from the repository-level snapshot mismatch. A snapshot-only inventory repair
   can be evaluated independently of their feature records because it preserves all feature refs and
   lifecycle files, but it does not make either feature usable or prove a safe full consumer cutover;
   cutover remains blocked until the required live-state and ownership checks can be established.
   For `SDD-OBS-001`, preserve the stale fingerprint/gate distinction; its telemetry diff requires the
   accepted authoring/review process and is not auto-reconciled here.
6. Recheck canonical state, attempts, leases, verification ownership, and active processes at the exact
   cutover checkpoint. Cutover remains blocked until all required facts are established through the
   supported bindings.

The procedure cannot be executed with the currently shipped recovery API. Harness must first close the
snapshot-reconciliation gap. The search did not recover authoritative lifecycle history for either
missing feature; `AH5-04B-QUAL-001` has evidence of past execution, while whether a lifecycle attempt
ever started for `SDD-001` remains undetermined. Both remain refused until a supported, non-guessing
disposition exists.
