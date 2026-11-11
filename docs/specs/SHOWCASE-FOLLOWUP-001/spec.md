# SHOWCASE-FOLLOWUP-001 - architecture review follow-up

Status: IMPLEMENTED - bounded verification PASS; hosted CI integration PASS at 0b18f3d; real-stack k6 smoke OPEN

The owner requested implementation of the four follow-ups proposed in the architecture review on 2026-11-10.

## Acceptance

- AC-001. Adapter dependency rules check actual source and destination adapter packages, including Kafka, Camel and AI. Shared adapter-common utilities and the existing web use of CurrentOperatorProvider remain allowed. A synthetic forbidden dependency must fail the real rule.
- AC-002. The k6 scenario uses an externally supplied bearer token, submits a valid catalog-backed order and reads orderNumber from the JSON response. Missing authentication, malformed responses and rejected placement fail visibly. Stock preparation is explicit and bounded by a configured iteration count.
- AC-003. Enabling Rabbit publishing alone creates no application listener container. The consumer has a separate default-off switch and context tests cover default, explicit-off and isolated explicit-on configurations.
- AC-004. Architecture diagrams include foundation and application orchestration. The current roadmap links completed execution separately from qualification and open decisions, and the backlog names actionable external dependencies.
- AC-005. Historical evidence and accepted S30-06 scope remain intact. The consumer switch establishes a default-off configuration boundary, with the S30-06 qualification and implementation gate still open.

## Verification and boundaries

Use Java regression tests for architecture rules and Spring context composition, Node's test runner for the real k6 module with synthetic transport, and repository documentation and evidence guards. Record runtime and infrastructure checks separately from isolated tests. New feature work is document-only for the harness inventory, with direct implementation authorized by the owner. This does not manufacture harness execution evidence.
