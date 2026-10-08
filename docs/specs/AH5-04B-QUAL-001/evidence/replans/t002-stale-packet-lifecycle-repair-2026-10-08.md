# T-002 lifecycle repair: stale legacy packet

## Finding

The reported `TASK_REPLAN_REQUIRED` is not on T-001. T-001 has no legacy packet and its packet identity is `unpublished`. The stale legacy packet is T-002: it is bound to feature fingerprint `ffc4791034cdaf8f68f8d4e821a9e67f4b96034dd3ba35c0f0f133fd206c8fb6`, while the accepted feature is generation 2. Its completed status prevents the legacy-packet resolver from treating that historical packet as a current execution contract.

T-002 was completed at attempt 3, the configured normal attempt limit. Reopen at the exhausted limit escalated T-002 without invalidating descendants. The owner-authorized resolution is recorded at `evidence/human-resolutions/T-002/20261008T084924.548898Z.json`. The unchanged current task object was replanned through the lifecycle CAS, from legacy revision `sha256:3e0f078571b50ac939991cb15ecd5403e415edfc4d2daa0ea36fa7bebe3da908` / contract `c1841f895c134dd426e5df7ac931176f4927f0ea2c9d4629c502e6dffa42d563` to revision `sha256:163acaaed09a545ae25c100705a91010141eff6e41799b22589f698d8903ac7c` / contract `493bf9c9d18906d0695aad15c62ff0b0f624458d56c8848373307e1095beab23`.

The normal `start` then refused before claiming because the existing T-002 worktree is stale: its feature fingerprint is `7b1b53779ffa0f133d2fad9f2f59431d0b2071d6e14793cf57524e056a9202e4`, while the accepted root feature fingerprint is `e67ff09153863952080c6fdb0316d31503e1da1abcf3becd8e0bb895e428fa4e`. The retry grant remains unconsumed; T-002 is still failed at attempt 3. T-003 through T-900 remain completed. Do not claim T-002 rework complete until a supported worktree refresh path permits its registered verification to run against the active packet.

The initial report that T-001 had the stale packet was disproved: T-001 has no legacy packet (`unpublished`). The failing identity was T-002.

## T-900 completion path

T-900 attempt 1 was escalated because its original packet invoked the absent `check_selected_report.py` and exited 2. The owner-authorized human resolution is recorded at `evidence/human-resolutions/T-900/20261008T082744.902283Z.json`. The accepted task-only replan is `evidence/planning/T-900-proposed-task-replan-2026-10-08.json`, producing packet revision `sha256:f63070106f278ec5be619aef9f35e2d9b552d486ab11c728d2c6db7a333057b2` and contract fingerprint `23dd6ba1dfc231382a7247a94f258e255878b10ab72c48b4bc2c1c83aed511dd`.

Attempt 2 ran the pinned Harness v0.5.0 checker against job `local-20261008T081503Z-3aff2f02a9fb`; it exited 0. Completion record `completion:sha256:6fc8fef93eafc947d5b27428c7bb6b02339869294b2169ec2e4ab22bf98fcc35` records that command, its result hash, packet revision, report job and the limited local-target claim. The selected report, full per-check evidence, checker output, reviewer report, and T-900 evaluation are now preserved under `evidence/selected/`. The report remains `launch_ready: false` with `BACKEND_UNAVAILABLE`.
