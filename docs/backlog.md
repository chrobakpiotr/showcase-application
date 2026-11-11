# Backlog

Open work for this repository, newest first. Remove an item when it is done; Git history keeps it.

The [roadmap](improvement-roadmap.md) summarizes completed execution, qualification and external dependencies.

- S30-06 remains deferred with an open design gate and no qualification. Re-entry requires the concrete target/control mechanism or owner-approved scope revision recorded in the [deferral decision](specs/S30-AMQP-POISON-001/evidence/s30-06-deferral-decision-2026-10-18.md). The application consumer defaults off independently of publishing.
- S05-07 requires the external handbook owner to apply and verify the [prepared delta](reviews/external-handbook-delta-2026-10-06.md). External completion has not been verified.
- A future required-sandbox live run needs active qualification for its exact checkout and policy, together with a tested provider profile. The v0.32.0 migration closeout is recorded in [Handover](handover/AGENT_HANDOVER.md).

- **Standing rule (owner, 2026-11-06):** after every commit that changes this project's code, update `docs/handover/AGENT_HANDOVER.md`. See "Handover file" in `AGENTS.md`.
- Claude provider: the owner's organization disables subscription access for Claude Code (403). The Mac has 2.1.319, which was unavailable in the follow-up environment. Once access is restored, use the tested 2.1.289 profile or collect structured-output evidence for 2.1.319 before registering it.
- Codex provider: collect structured-output evidence for Codex CLI 0.166.0 before registering its task-result profile. The CLI reported 0.166.0 and listed `--output-schema`, but the follow-up sandbox lacked `/proc/self/exe` and `codex exec` stopped before sending the local fixture a request. Use the tested 0.160.0 first on `PATH` until then.
