# Backlog

Open work for this repository, newest first. Remove an item when it is done; Git history keeps it.

- **Standing rule (owner, 2026-11-06):** after every commit that changes this project's code, update `docs/handover/AGENT_HANDOVER.md`. See "Handover file" in `AGENTS.md`.
- Preserve the earlier FIRST-RUN branches and escalated state until the owner decides their disposition.
- After 1 December 2026: confirm main and Agentic SDD CI on the 0.31.1 adoption commit (no hosted CI ran for it).
- Claude provider: the owner's organization disables subscription access for Claude Code (403), and the Mac now has Claude Code 2.1.319, while the tested task-result profile is 2.1.289. Before using `--provider claude` again: org access, plus either 2.1.289 or a tested 2.1.319 profile.
- Codex provider: add a tested task-result profile for the owner's default Codex CLI (0.166.0); until then runs need 0.160.0 first on `PATH`.
- The Codex agent could not run `harness.py doctor` inside its sandbox (`No module named 'agent_harness'`): the agent environment lacks the pinned library. Decide whether agents should have it.
