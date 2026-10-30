# Independent review: Mac recovery and later local code adoption

Verdict: **no unresolved blockers in the prepared runbook and kit**. This review does not establish live-host readiness and does not execute acknowledgement or local code adoption. The owner authorization is already recorded; the remaining gates are technical prerequisites.

Reviewed runbook SHA-256: `dc0eb85604ba3409ca2805bd57a7a567ab9298a574d5759df52e13f452d13f3b`.
Reviewed kit SHA-256: `98e03ec813288348746ac606d9b270006fa482a9a8a69bed0a4abddeb90fcecc`.

## Scope and correctness

Read the complete runbook, frozen acknowledgement README/source, historical-runtime source, preconditions report, and the actual protected consumer registry loader. The described helper signatures and four CLI flags match the source.

- The protected old consumer and separate installed 0.20.0 runtime are preserved before any live 0.21.1 update. The installed library version, metadata, module location, and exact implementation digest are checked. The runbook does not substitute the current runtime for the historical acknowledgement pin.
- The operation stays fixed to API-ACCESS-001, the two old refs at `d41f0aa9e6b8d6f5d011024bb5c29ac647f7c9cd`, and the single empty regular mode-0644 map.lock addition. The real expected snapshot ID comes from the validated live manifest; there is no invented ID or CLI override.
- Writer freeze, recorded drain, real repository/worktree/common-directory binding, registry/marker retention, and valid sandbox denial for both audit and registry are explicit stop conditions. Same-user mode 0700 is correctly not treated as sandbox proof. The runbook does not authorize a model/sandbox canary.
- The unknown SDD-001 and AH5-04B-QUAL-001 histories remain unresolved and blocked. No marker/registry deletion or false recovery claim follows from snapshot acknowledgement. A refusal, interruption, or incomplete audit is not success; retry requires reconciliation and exact audited inputs.
- Actual local changes, ignored/untracked telemetry, operational artifacts, lifecycle refs/objects, and the protected old code/runtime are preserved before checkout changes. No automatic pull, force reset, blanket wrapper copy, or state rollback is instructed.
- Local code adoption follows acknowledgement and its fresh verification, exact adoption-commit checks, and full live destination review. It uses consumer commit `d5103ae493ee69d579445be3625222bf7789b2cb` and immutable library pin `ebf9a704791f5f1a583afeb577556326e30ae46b`. The protected e5b54ada consumer with its 0.20.0 pin remains the rollback pair.
- Normal Wayfinder/control-plane operation remains blocked pending the separate reviewed operational-data relocation. Code adoption does not imply that relocation or live-state safety.

## Verification executed by reviewer

- All eight embedded Bash blocks pass `bash -n`; all embedded Python heredocs parse successfully.
- The frozen tool SHA-256 and retained 0.20.0 wheel SHA-256 match the exact constants in the runbook. The source-confirmed installed implementation digest is correctly distinguished from the consumer import wrapper.
- The latest recovery-kit paragraph and KIT_DIR bindings point to the actual archive paths. The kit contains the reviewed runbook bytes and unmodified frozen tool. All nine payload checksums independently pass; the kit archive hash is recorded above.

No Mac command, host sandbox experiment, live acknowledgement, local code update, or remote write was executed by this reviewer. Live-state predicates remain unobserved until the operator verifies them on the authoritative Mac.
