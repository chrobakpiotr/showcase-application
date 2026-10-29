# Private Harness access in CI

The Agentic SDD and sandbox qualification workflows install the full commit pinned
in `tooling/agent-harness/requirements.txt`. Because Harness is private, configure
an Actions repository secret named `HARNESS_READ_TOKEN` in Showcase (and separately
in agent-benchmark). Use a fine-grained token with an expiry, limited to
`chrobakpiotr/agent-harness` and **Contents: read-only**. No write or administration
permission is needed. Never put the token in source, logs, or a chat message.
The default `GITHUB_TOKEN` cannot read another private repository.

The shared install action checks out that commit without persisting credentials,
then redirects pip's unchanged VCS URL to the local checkout. This preserves the
installed commit identity and keeps the token out of dependency build/test steps.
The temporary Git URL mapping is removed even if installation fails.

Missing credentials fail with an explicit error. Fork pull requests do not receive
repository secrets and cannot run these private-dependency gates. Do not switch to
`pull_request_target` or grant secrets to untrusted fork code to bypass this boundary.
After configuring the secret, rerun failed jobs or dispatch Agentic SDD CI.

Verification: parse both workflows and the composite action, check every installer
uses the action, install the pinned dependency locally, and run Harness regression
checks. Runner qualification still requires its dedicated GitHub-hosted environment.
To roll back the workflow wiring, revert the CI fix; the private dependency will
again require authenticated local Git access. Harness remains private throughout.
