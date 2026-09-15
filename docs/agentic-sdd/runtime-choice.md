# Runtime choice: native harness vs orchestration frameworks

## Decision

Keep the **repo-native Agentic SDD execution engine** as the default runtime for this project. Do not add LangGraph (or an
equivalent graph/workflow framework) to the production harness today. Keep the protocol/runtime boundary modular so the
execution backend can be reconsidered later if the operating model changes.

## What was evaluated

The comparison focused only on the generic runtime responsibilities where a mature graph framework is strongest:

- DAG scheduling and bounded parallelism;
- retry and failure recovery;
- durable state/checkpointing;
- human-in-the-loop pause/resume;
- long-running workflow persistence;
- fan-out/fan-in execution.

It did **not** treat the following as framework-replaceable because they are repository-specific engineering semantics:

- Wayfinder discovery, Decision Ledger, fog/frontier and reconciliation;
- Spec/Plan/Verification Contract (`AC-*`/`VC-*`);
- risk-driven TDD/test seams;
- context-trust policy;
- immutable task packets and `allowed_paths`;
- Git worktree isolation and dependency checkpoint composition;
- specialist reviewer routing and independent evaluator behavior;
- deterministic JVM/Gradle/security verification;
- behavioral harness evals and local integration policy.

## Measured native-runtime signal

A synthetic benchmark deliberately used a provider that slept for only ~50 ms so orchestration overhead dominated. Representative
results were approximately:

| Operation | Native result |
| --- | ---: |
| load state → compute ready → save (1000 iterations average) | ~1 ms |
| task worktree creation | ~0.69 s |
| task worktree removal | ~0.65 s |
| 3 parallel builders → evaluator | ~5.55 s |
| one builder failure → retry → evaluator | ~8.21 s |
| expired-lease recovery | ~0.69 s |

The important finding is not the exact number; it is the shape of the cost. Git worktrees, process spawning, provider execution,
verification and checkpoint composition dominate. Replacing the in-process scheduler/state code does not remove those costs.

The evaluation estimated that a generic workflow runtime would replace only a modest portion of the total harness because most
code implements the software-engineering protocol above rather than generic state-machine mechanics.

## LangGraph-specific strengths acknowledged

A mature graph runtime remains stronger in several areas:

- first-class durable checkpoint stores;
- native interrupts and resume across long time gaps;
- centralized thread/run history and time travel;
- multi-process/multi-machine execution patterns;
- production service operation around long-lived workflows.

Those strengths matter most when the harness is operated as a platform/service rather than as a local developer tool.

## Why the native runtime wins for the current use case

The current operating model is one developer running local Codex/Claude against one repository, with Git as the durable artifact
and runs usually measured in minutes rather than days. In that environment the native runtime gives:

1. **Lower cognitive load.** State is visible in repo artifacts, `.agent-state`, `.agent-runs` and Git worktrees.
2. **Better side-effect alignment.** Git/worktree/checkpoint semantics remain explicit instead of being hidden behind a second
   graph/checkpoint abstraction.
3. **No duplicate persistence model.** There is no separate workflow database/thread/checkpoint namespace to reconcile with Git.
4. **Small measured scheduler cost.** Generic state transitions are not the bottleneck.
5. **Precise retry semantics.** Completed sibling tasks are preserved, stale leases are recovered and evaluator-directed rework
   can reopen only the smallest relevant builder set.
6. **Auditable HITL without a framework.** `needs-human` now pauses the DAG; `human-resolve` records the accepted decision, grants
   exactly one explicit resume authorization and continues without erasing attempt history.

## The HITL gap found during the framework evaluation

The evaluation exposed one real native-runtime weakness: an escalated task could pause the DAG correctly, but there was no
first-class task-level resume command. That gap has been fixed instead of adding an entire orchestration dependency.

The supported lifecycle is now:

```text
running
  ↓ needs-human
escalated
  ↓ explicit human-resolve + audit artifact
failed/ready + one-shot resume grant
  ↓ next orchestrator invocation
running
  ↓
completed | failed | escalated
```

The resume grant does not reset the attempts counter and cannot be reused. Accepted human-resolution artifacts live under the
feature evidence directory and are trusted only within the existing accepted contract. Contract-changing decisions still require
normal spec/plan/task updates and deliberate state reset/re-planning.

## Evaluation limitation

The native engine was executed and benchmarked directly. The external graph runtime was evaluated from its current documented
execution model/API and a reference integration spike; the evaluation environment did not permit installing the external package,
so this was **not** presented as a runtime performance benchmark of LangGraph itself. The decision therefore rests on measured
native bottlenecks, architecture/ownership analysis and expected replacement scope-not on a claim that the native scheduler is
faster than LangGraph.

## Revisit triggers

Re-open this decision if one or more become true:

- workflows routinely run for many hours or days;
- approvals must survive developer-machine shutdowns and resume much later;
- multiple developers/workers execute one graph concurrently across machines;
- a central web UI/API needs durable shared workflow state;
- workflow state must survive service restarts independently of local Git state;
- a queue/worker architecture becomes necessary;
- maintaining leases/recovery/HITL locally becomes a measurable reliability burden in the checked-in eval suite.

Until then, framework adoption would add operational surface without removing the dominant worktree/provider/verification costs.
