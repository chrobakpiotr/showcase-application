# Plan - PAYMENT-CAPTURE-IDEMP-001

1. Document the external-capture unknown-outcome window.
2. Make provider idempotency identity explicit in `ChargePaymentOutPort`.
3. Use `ORDER-CAPTURE:<orderNumber>` for every retry of one logical capture.
4. Make the mock gateway deterministic for the same operation id.
5. Add focused domain and adapter regression tests.
6. Run SDD validation, focused tests, full tests and full build.
