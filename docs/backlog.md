# Backlog

Open work for this repository, newest first. Remove an item when it is done; Git history keeps it.

- **Standing rule (owner, 2026-11-06):** after every commit that changes this project's code, update `docs/handover/AGENT_HANDOVER.md`. See "Handover file" in `AGENTS.md`.
- After 1 December 2026: confirm main and Agentic SDD CI on the 0.31.1 adoption commit (no hosted CI ran for it).
- Claude provider: the owner's organization disables subscription access for Claude Code (403). The Mac has 2.1.319, which was unavailable in the follow-up environment. Once access is restored, use the tested 2.1.289 profile or collect structured-output evidence for 2.1.319 before registering it.
- Codex provider: collect structured-output evidence for Codex CLI 0.166.0 before registering its task-result profile. The CLI reported 0.166.0 and listed `--output-schema`, but the follow-up sandbox lacked `/proc/self/exe` and `codex exec` stopped before sending the local fixture a request. Use the tested 0.160.0 first on `PATH` until then.
