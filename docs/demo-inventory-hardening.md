# Demo inventory hardening

## Scope and acceptance criteria

This focused fix preserves the existing inventory API and authorization model.
Allowed implementation paths are the inventory component, template, styles and tests.

- At most one lookup or stock adjustment may be in flight in this component.
- Starting a lookup clears the previously loaded stock, including when it fails.
- While a request is pending, lookup and adjustment controls are disabled.
- Success and failure both release the pending state so the user can continue.
- Only the selected adjustment service method is invoked.
- The SKU field offers seeded examples without issuing a mutation, and adjustment
  buttons wrap on small screens. Pending work is announced as an accessible status.
- A failed adjustment is not automatically retried: its server outcome can be
  unknown and receiving stock twice is unsafe.

## Verification

Use delayed RxJS Subjects to exercise duplicate clicks and transitions between
lookup and adjustment. Keep the existing 100% coverage thresholds. Run frontend
lint, unit coverage and production build. No backend contract or SDD-001 hash
input is changed.

## Limits and follow-up

This prevents duplicate requests within one mounted component. It does not
provide server-side idempotency across tabs, reloads or network retries.
That requires a separately specified backend contract. Other mutation screens
(returns, reviews and shipments) should receive the same delayed-response audit.
