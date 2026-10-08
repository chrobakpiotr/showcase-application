# AH5-04B scope replan — Harness handoff 2026-10-07

The human-provided Harness handoff and the attached Harness review authorize this scoped revision of AH5-04B-QUAL-001:

- Pin `agent-harness[grants]` to v0.4.0 at commit `092b8d761b6ad4d8b7c2e31765cc273e8c5a573e`; preserve `requirements.txt` in the protocol fingerprint.
- B8's 256 MiB writable quota covers `/workspace` plus `/tmp`; timeout handling stops the container by ID and records its state.
- Exception details in committed evidence are bounded and exclude secrets.
- B10 records and validates contract-v2 request/result semantics; the fictional golden fixture is not evidence.
- Pin GitHub Actions by full commit SHA.

This does not qualify a target, authenticate local evidence, create a grading backend, or authorize payload execution. Docker Desktop remains first; a fresh GitHub-hosted job is only the fallback after a non-passing Docker result. Each job has independent evidence and review.
