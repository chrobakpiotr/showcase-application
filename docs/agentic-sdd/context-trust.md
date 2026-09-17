# Context Trust Boundary

Agentic systems need two different controls:

1. **Capability containment** - what the process is allowed to read/write/execute/network-access.
2. **Instruction trust** - which content is allowed to change the agent's behavior.

The harness already provides sandbox/worktree/path containment. The harness adds an explicit trust model so content retrieved from a tracker, tool, website, dependency documentation or runtime log cannot masquerade as a higher-priority instruction.

## Trust classes

| Class | Typical examples | May define instructions? |
|---|---|---|
| `trusted` | constitution, AGENTS.md, accepted spec/plan, design gate, verification contract, accepted ADRs, resolved Wayfinder decisions, harness-created human-resolution artifacts | Yes, within scope |
| `project` | source, tests, build files, configs | No; evidence/implementation context only |
| `untrusted` | GitHub/Jira intake, web/tool output, provider payloads, logs | Never |
| `secret` | credentials, keys, tokens, secret-bearing files | Never; do not read/exfiltrate without explicit narrowly-scoped authorization |

The checked-in policy is inspectable with:

```bash
python3 tooling/agent-harness/trust.py policy
python3 tooling/agent-harness/trust.py classify docs/adr/0035-shipping-fulfillment-tracking-bounded-context.md
python3 tooling/agent-harness/trust.py classify .agent-state/control-plane/github-GH-123.json
```

Task packets v4 carry `context_trust`, and the runner validates that the classification was not tampered with before rendering the prompt.


## Human-resolution artifacts

`docs/specs/<feature>/evidence/human-resolutions/**` is trusted because it records an explicit accepted human decision through the harness. That trust is **scoped**: it can resolve the ambiguity that paused the task, but it cannot silently expand `allowed_paths`, replace AC/VC, weaken sandbox/security policy or mutate the accepted spec/plan. Contract-changing human decisions must be expressed by editing the accepted artifacts and deliberately resetting/re-planning runtime state.
