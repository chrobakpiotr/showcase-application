# SDD-OBS-001 Spec Grill round 1 resolution

Status: RESOLVED — pending fresh Spec Grill confirmation

Round-1 result: `needs-human`
Prototype recommendation: none

The round-1 grill identified 14 contract blockers. They were resolved by strengthening the normative
specification rather than by prototyping.

Key compatibility decision: accepted SDD-001 outer-runner semantics remain unchanged. In
`task-completion` mode every task-declared verification command is freshly executed after builder PASS.
Smart verification reuse is available only where the invocation policy allows it, principally
continuation/integration verification.

See `spec.md` and `plan.md` for the complete BQ-01..BQ-14 resolutions. No executable task DAG may be
created until a fresh Spec Grill and Architecture Grill both pass, followed by a current design gate and
independently accepted verification contract.

