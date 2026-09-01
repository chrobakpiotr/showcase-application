# Security Policy

This is a personal portfolio/showcase project, not a production service handling real user data.
It is not actively monitored for incoming vulnerability reports on a guaranteed timeline, but
reports are still welcome and genuinely appreciated.

## Reporting a vulnerability

Please use GitHub's [private vulnerability reporting](../../security/advisories/new) feature
(Security tab -> Report a vulnerability) rather than filing a public issue, so any real
finding isn't disclosed before a fix is available.

Alternatively, open a regular GitHub issue for anything you'd be comfortable disclosing publicly
(e.g. a hardening suggestion rather than an exploitable flaw).

## Supply-chain security posture

This repository already applies a number of automated supply-chain safeguards, documented in
[ADR 0016](docs/adr/0016-cicd-supply-chain-hardening.md):

- Dependabot version-update PRs for Gradle, npm, Docker, Terraform and GitHub Actions
  dependencies (see [`.github/dependabot.yml`](.github/dependabot.yml))
- OWASP DependencyCheck and a PR-time dependency-review gate against known-vulnerable/
  incompatibly-licensed dependencies
- Trivy container image scanning and a CycloneDX SBOM published on every build
- CodeQL static analysis and a weekly OpenSSF Scorecard scan, both reporting to GitHub Code
  Scanning
- every third-party GitHub Action pinned to a full commit SHA

## Scope

Given the nature of this project (a local-only, Docker Compose/Kind-based showcase with no
publicly hosted deployment), most realistic findings will be about code quality, dependency
hygiene, or configuration hardening rather than an actively exploitable live system.
