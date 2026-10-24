# Showcase retry-control API: pre-adoption digest evidence

Date: 2026-10-24

## Source binding

- Showcase source checkpoint: `d0679550c478aac01d2bec541d7fee77a05eb1e1`.
- Showcase pin: agent-harness v0.17.0, release SHA
  `137b0ba057c4d69c94fc1d8b04b0af8d3b0b039e`.
- The Harness source files used for the comparison matched that release SHA:
  - `src/agent_harness/verification/retry.py`: `447f33693dae985d853a09848f8653e9a1aa7bf6`
  - `src/agent_harness/verification/serialization.py`: `4672db63f0f3bfb359b444b851172ba8605e4b95`
  - `src/agent_harness/verification/supervisor.py`: `cb33a111721aa826ebb6ba0545c8fa5dfbe28ccb`

## Comparison

For each registered Showcase retry control, compared the existing
`_retry_control_inputs_digest(control_id)` with the pinned library type's
`RetryControl.inputs_digest()`. Both use SHA-256 over the canonical payload
containing the `control_id` and lexically sorted `inputs` mapping.

| Existing control ID | Existing helper digest | `RetryControl.inputs_digest()` | Match |
|---|---|---|---|
| `critical-postgres-gate-disables-gradle-test-retry-v1` | `ba34b24749900a92c69b12b024e171fe3920343aced5fb05e8b76984d8b0707d` | `ba34b24749900a92c69b12b024e171fe3920343aced5fb05e8b76984d8b0707d` | yes |
| `critical-rabbit-gate-disables-gradle-test-retry-v1` | `dd476704327344e29951c4a579b687f4a0c6e61a60bad8f5e20bb38c73397afc` | `dd476704327344e29951c4a579b687f4a0c6e61a60bad8f5e20bb38c73397afc` | yes |

This records the already-run comparison only. It makes no change to control IDs,
retry policy, or historical evidence.

## Boundary of this evidence

This is the pre-migration baseline; it does not establish the future public API
binding or callback refusal behavior after adoption. After Harness releases the
additive `agent_harness.retry_control.RetryControl` API, Showcase adoption must
separately verify that both digests remain identical and that callback errors
are refused as `retry-policy-violation` through the real Showcase integration.
