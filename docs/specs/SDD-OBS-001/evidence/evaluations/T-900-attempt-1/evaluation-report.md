# T-900 independent evaluation — attempt 1

**Result: PASS.** The corrected accepted candidate composition passed all nine exact packet verification commands. No implementation finding remains. The evaluator-only test updates address stale test assumptions and preserve guard sensitivity; no production source was changed.

## Evaluated target and composition

- Candidate implementation checkpoint: `7de8e69998cd152ea2b8b93773c191d3749d38bd`, comprising accepted T-001 through T-009 checkpoints, including T-008 checkpoint `05f9a227`, with the recorded current-main maintenance overlays and evaluator-owned test corrections described in `verification/22-final-linux-discovery/source.txt`.
- Linux test environment: standalone clone `/tmp/showcase-t900-isolated-retry-20261013-7f2a6d` with its own `.git`; image `showcase-t900-eval:pinned-20261013@sha256:bb7600c8e1542e7eac4706f14dca1b78c6e043b19f8f61d226faa906c695c4c5`; Python 3.13.16, Harness v0.5.0 (`16bb93821292096b94889306288ee2caec5f4006`), `cryptography==49.0.0`. Exact composition/source and test-file hashes are in `verification/22-final-linux-discovery/`.
- Command 3 was run in the canonical primary checkout at `/Users/pau/IdeaProjects/showcase-application`; captured CWD and git root both match that path. The T-900 linked worktree invocation is retained as diagnostic because its ignored lifecycle packet is materialized only by the primary checkout. Container attempts were also diagnostic because snapshot repository identity is path-bound. Neither diagnostic result is counted in place of the passing canonical invocation.
- This is evaluator evidence, not a fresh Docker target qualification or production readiness claim.

## Exact packet verification

| # | Exact packet command | Result |
|---|---|---|
| 1 | `python3 -m unittest discover -s tooling/agent-harness/tests -v` | **PASS**, exit 0; 920 tests, 469.820s. Raw output and source provenance: `verification/22-final-linux-discovery/`. |
| 2 | `python3 tooling/agent-harness/harness.py validate-all docs/specs` | **PASS**, exit 0. |
| 3 | `python3 tooling/agent-harness/orchestrate.py docs/specs/SDD-OBS-001 --plan` | **PASS**, exit 0 in canonical primary checkout. CWD/git-root/output/exit are captured in `verification/23-packet-commands/03-orchestrate-primary-host.*`. |
| 4 | `python3 tooling/agent-harness/verification_contract.py validate docs/specs/SDD-OBS-001` | **PASS**, exit 0. |
| 5 | `python3 tooling/agent-harness/eval.py run --suite baseline --repeat 1` | **PASS**, exit 0. |
| 6 | `python3 tooling/agent-harness/eval.py run --suite adversarial --repeat 1` | **PASS**, exit 0. |
| 7 | `python3 tooling/agent-harness/eval.py run --suite shipping-preflight --repeat 1` | **PASS**, exit 0. |
| 8 | `python3 tooling/scripts/check_markdown_links.py` | **PASS**, exit 0. |
| 9 | `python3 tooling/scripts/generate-agentic-sdd-handbook-pdf.py --check` | **PASS**, exit 0. |

Raw command, stdout, stderr, exit code, and result artifacts are retained in `verification/22-final-linux-discovery/` and `verification/23-packet-commands/`. The 144 accepted criteria are cross-referenced to the current DAG owners and their active completion-record IDs in `acceptance-coverage.json`.

## Counterexamples, corrections, and mutation evidence

- Replaced the T-900 stale `shutil.which` mock with the shared `resolve_executable` seam and asserted the legacy lookup is not used.
- Updated the profile showcase test from current main with only the candidate API-required `repository=ROOT` fixture binding. It passes 7/7; the current-main applicability tests pass, so the earlier stale profile finding is not current.
- Updated the origin fingerprint fixture to use valid `Family.origin_policy` variants and matching profile hashes; focused test passes.
- Made E33 cleanup assert absence of live process-group members, not disappearance of a zombie-only group. The test still refuses a live descendant. Focused E33 module passes 29 tests. `_group_status` tests prove zombie-only is drained, live is not drained, and unavailable process inspection remains fail-closed.
- Two mutation probes independently disabled the zombie and live-member guards; each caused the focused test to fail (exit 1). The disposable mutant was restored to the production module hash recorded in `verification/21-e33-mutations/`.
- Same-image diagnostic confirms Q05 PASS means process-group association, not drainage. After cleanup, only a zombie remained; `_group_status` returned `UNCERTAIN` because the slim image has no `/bin/ps`, preserving fail-closed behavior. See `verification/19-e33-status-diagnostic/`.
- Historical failed runs and their causes are preserved under verification directories 11–21 and are superseded by the final exact run. They are not used as passing evidence.

## Verdict and lifecycle

No candidate-owned defect was reproduced in the final composition. The final nine packet checks pass, and no `.agent-state` file was edited. The evaluator result is PASS, ready for lifecycle checkpoint/review. The recommended next command, from the primary checkout, is the normal T-900 `complete` lifecycle command with owner `t900-evaluator`, binding this report, `acceptance-coverage.json`, and the verification manifest as completion evidence; do not copy state or invoke completion from the linked worktree.
