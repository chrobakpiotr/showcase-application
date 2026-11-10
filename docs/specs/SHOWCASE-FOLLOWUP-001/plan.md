# Implementation plan

The specification in [spec.md](spec.md) owns acceptance. Execute the following bounded slices in this session.

1. Add failing architecture-rule fixtures and AMQP application-context tests. Correct Adapter and ArchitectureElement, make every adapter visible to domain architecture tests and refresh the active build-input registration when its input changes. Preserve recorded evidence.
2. Add failing Node tests against the k6 entry point. Require ACCESS_TOKEN, use a complete order and JSON orderNumber, bound iterations, and prepare sufficient stock in setup on a disposable demo stack.
3. Add a default-off service.rabbitmq.consumer.enabled condition to the listener container. Keep publishing controlled by the existing service.rabbitmq.enabled property. Verify both configuration boundaries with synthetic connections.
4. Update architecture, testing, local-development and AMQP operational guidance. Reconcile the current roadmap, backlog and handover with the dated records. Regenerate the specification inventory.
5. Run focused regressions, formatting and relevant quality gates, then the available full gates. An independent evaluator reviews the final diff against AC-001 through AC-005. Report unavailable infrastructure evidence explicitly.

## Review focus

- A forbidden dependency in any new adapter must be caught even when no matching bounded context exists in another adapter.
- The operator identity dependency is the only web-to-security exception.
- Missing or expired access tokens and invalid placement responses must stop the load run without logging token contents.
- Stock prepared for a bounded run must cover its maximum order count. Preparation and placement must be scoped to an isolated demo environment.
- Rabbit publisher configuration must not accidentally instantiate a consumer. A default-off listener does not establish S30-06 qualification.
