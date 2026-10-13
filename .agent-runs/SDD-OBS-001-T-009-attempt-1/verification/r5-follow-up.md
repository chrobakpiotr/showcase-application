# T-009 Harness review R5 follow-up

Run date: 2026-10-13. Candidate R5 change: commit `6a4caeb`.

- Admission and launch consumption both return the plan's stable `VERIFICATION_ATTEMPT_BINDING_MISMATCH` when the live task attempt differs from the accepted plan.
- The consumption test was first run against the old code and failed because it returned `VERIFICATION_EXECUTION_PLAN_STALE`; after the fix, both exact-code tests passed.
- Mutation checks disabled only the attempt comparison in `admit_verification_execution` and `consume_verification_launch`, one at a time. Each corresponding exact-code test failed because no `StoreError` was raised: both mutations were CAUGHT.
- Focused Linux suite: executor, supervisor, store and lifecycle modules; 132 tests passed.
- Runtime: Python 3.13.16, cryptography 49.0.0, Docker image `showcase-t003-python313-crypto49-git:latest`, image ID `sha256:cc903239c8ac2ca9633fa621cc9f8f0ae30678b0d938567a0e38df8e8b69641c`.
- The round-2 results note corrects the earlier wording: Harness asked tests to assert stable structured outcomes; it did not prohibit those identifiers. The plan lists `VERIFICATION_ATTEMPT_BINDING_MISMATCH` as a stable failure.
