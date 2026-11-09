# DEMO-004 implementation plan

1. Add a cart action guarded by the current `ORDER_WRITE` role and the confirmed
   non-empty snapshot. Send a typed copy of the line-item compatibility fields
   and coupon using Angular Router navigation info. Lock cart actions until that
   navigation settles.
2. Track the cart screen's six writes in a root service keyed by cart ID. Reject
   overlapping cart actions while a write is pending. Keep the write subscribed
   until it settles, even if the route is destroyed, and wait for settlement
   before reading the stored cart ID on re-entry. Refresh the same cart on a
   write error, with explicit retry if the refresh fails. Dispose route read
   subscriptions and UI observers on destruction.
3. Capture navigation info during creation of the order component and seed only
   the line items and coupon. Restore and validate the stored order attempt first.
   Invalid stored attempts clear storage and leave a blank form or the current
   cart draft. Require at least one valid order line before a new submission.
4. Use Angular DOM tests, delayed observables and real router activation to
   verify the action, re-entry ordering and attempt precedence. Extend the
   existing Playwright response-loss test through Cart and document the demo.
   Verify with the frontend unit suite, lint, build and disposable E2E stack
   when the local environment supports them.
