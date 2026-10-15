# T-900 independent evaluation — attempt 2

**Result: PASS.** All nine declared packet commands pass on the current accepted task composition. The first full-discovery run found two evaluator-test defects; the existing reviewed T-900 test-only corrections were reapplied, the affected module passed 29/29, and the exact full-discovery command then passed 923 tests. No production implementation change was needed.

## Evaluated composition

- T-900 worktree candidate parent: `141d5f48135cd2b63d95dc93a34ba2b9e13317c2`; it includes accepted T-001 through T-009 checkpoints, including T-007 `527a51e9…` and the fresh T-008 checkpoint `51d21a21…`. T-900 changes are evaluator-only tests plus evidence.
- Pinned Linux image: `showcase-t900-eval:pinned-20261013@sha256:bb7600c8e1542e7eac4706f14dca1b78c6e043b19f8f61d226faa906c695c4c5`, linux/amd64, Python 3.13.16, cryptography 49.0.0.
- The primary checkout was mounted read-only for Linux tests. The task worktree was the writable candidate. The orchestration `--plan` command ran in the canonical primary checkout, where the lifecycle packet is materialized.
- This is evaluator evidence only; it does not qualify a new backend target or claim production readiness.

## Packet commands

| # | Command | Result |
|---|---|---|
| 1 | `python3 -m unittest discover -s tooling/agent-harness/tests -v` | **PASS**, 923 tests, 649.651 s, pinned Linux image. |
| 2 | `python3 tooling/agent-harness/harness.py validate-all docs/specs` | **PASS**. |
| 3 | `python3 tooling/agent-harness/orchestrate.py docs/specs/SDD-OBS-001 --plan` | **PASS** in canonical primary checkout. |
| 4 | `python3 tooling/agent-harness/verification_contract.py validate docs/specs/SDD-OBS-001` | **PASS**. |
| 5 | `python3 tooling/agent-harness/eval.py run --suite baseline --repeat 1` | **PASS**, all 6 cases. |
| 6 | `python3 tooling/agent-harness/eval.py run --suite adversarial --repeat 1` | **PASS**, all 7 cases, pinned Linux image. |
| 7 | `python3 tooling/agent-harness/eval.py run --suite shipping-preflight --repeat 1` | **PASS**, all 4 cases. |
| 8 | `python3 tooling/scripts/check_markdown_links.py` | **PASS**, 291 files. |
| 9 | `python3 tooling/scripts/generate-agentic-sdd-handbook-pdf.py --check` | **PASS**, generated PDF is current. |

The first Mac adversarial diagnostic failed only because cryptography 49.0.0 is unavailable in that host environment; A006 and A007 both passed in the pinned Linux rerun. The initial Linux discovery exposed the stale `shutil.which` test mock and a zombie-only group assertion; after applying the prior reviewed test-only fix, the exact command passed. Initial diagnostics and passing reruns are retained separately.

## Scope and outcome

- T-900’s two resolver exclusion tests run under `pathlib.Path.home()` and assert the exact refusal codes. The corrected backend-discovery test proves `resolve_executable` is used; the prior Harness review independently confirmed each guard mutation is caught.
- Evaluator changes are limited to `tooling/agent-harness/tests/**`; no production module changed.
- The 144-entry acceptance crosswalk binds each accepted criterion to current completed task evidence and all nine evaluator gates.
- No candidate-owned implementation defect remains. Preserve the M5.3 Design Gate status as **NOT PASS** and do not claim backend/production qualification from this evaluation.
