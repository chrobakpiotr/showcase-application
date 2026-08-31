# 0016. CI/CD supply-chain hardening (SHA-pinned actions, dependency review, OpenSSF Scorecard)

## Context

The CI/CD pipeline itself is part of the software supply chain: every third-party GitHub Action
referenced in a workflow runs with access to the repository checkout and, depending on the job,
secrets and write-scoped tokens. A workflow that references an action by a mutable tag (e.g.
`@v4`) trusts that tag to keep pointing at the code it pointed at when the workflow was written -
a compromised upstream repository (or a compromised maintainer account) can re-point that tag at
malicious code with no visible change to this repository's own history, and the next CI run
silently executes it. Similarly, an over-broad or workflow-level-only `permissions:` block means a
single compromised step in an otherwise low-risk job (e.g. a documentation lint) could carry
write access it never actually needed.

## Decision

- **Pin every third-party action to a full commit SHA**, with the human-readable version as a
  trailing comment (e.g. `uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09 # v5.1.0`)
  across every workflow (`ci.yml`, `codeql.yml`, `scorecard.yml`). A SHA is immutable by
  construction, so this removes "the tag started pointing somewhere else" as an attack vector
  entirely; the version comment keeps `git blame`/review and future manual upgrades readable
  without needing to resolve the SHA back to a version first.
- **Give every job its own least-privilege `permissions:` block** instead of relying on the
  workflow-level default, scoped to exactly what that job's steps need (typically `contents: read`
  only; `security-events: write` only for the two jobs that actually upload SARIF; `id-token:
  write` only for the Scorecard job's OIDC-based results publishing).
- **Add a `dependency-review` job**, gated to `pull_request` events, using GitHub's
  `dependency-review-action` to fail a PR immediately if it introduces a new dependency (any
  ecosystem the PR touches - Gradle, npm, or the Docker base image) with a known high-severity
  vulnerability or an incompatible license - a fast, diff-scoped complement to the existing
  full-tree OWASP DependencyCheck/Trivy/SBOM jobs, which scan everything on every run regardless
  of what changed.
- **Add an OpenSSF Scorecard workflow** (`scorecard.yml`) that runs on push to `main` and weekly,
  publishing results to the public `scorecard.dev` API/badge and to the repository's Code Scanning
  dashboard. Scorecard checks dozens of supply-chain best practices this ADR and the rest of the
  pipeline already implement (pinned dependencies, branch protection, SAST tooling, absence of
  dangerous workflow patterns) as a single continuously-tracked score, and its own results-
  publishing API enforces structural constraints on the producing workflow (no top-level
  env/defaults, no workflow-level write permissions, a fixed allow-list of permitted steps), which
  the new workflow follows exactly per Scorecard's own documented reference example.

## Consequences

- Upgrading a pinned action now requires updating both the SHA and its comment (rather than a tag
  bump alone); this is a deliberate, small amount of added friction in exchange for removing an
  entire class of supply-chain compromise.
- The `dependency-review` job only ever runs on pull requests (GitHub's API for it requires a base
  and head ref to diff against), so it is additive to, not a replacement for, the existing
  always-on dependency scans.
- The Scorecard badge and dashboard make the repository's supply-chain posture visible and
  independently verifiable to anyone (a recruiter, a reviewer, another engineer) without needing
  to read the workflow YAML themselves.
